package kr.suhsaechan.secbrief.advisory.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyCall;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyCallRunner;
import kr.suhsaechan.secbrief.advisory.ai.schema.OpenAiJsonSchemaConverter;
import kr.suhsaechan.secbrief.advisory.constant.AiProviderCode;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenAI 제공자: 요청 1회 > 키 우선순위대로 POST {base-url}/v1/responses (text.format 에 strict JSON 스키마) > 응답 JSON 문자열
 * 키 순회·키마다 호출 기록 1행은 AiApiKeyCallRunner, 쓸 키가 없으면 호출하지 않음. 응답은 저장하지 않게 요청 (store false)
 */
@Component
public class OpenAiStructuredCompletionClient implements StructuredCompletionClient {

    public static final String PROVIDER_CODE = AiProviderCode.OPENAI;
    private static final KeyedAiProvider PROVIDER = KeyedAiProvider.OPENAI;

    private static final String RESPONSES_PATH = "/v1/responses";
    // text.format.name (필수): 스키마 이름
    private static final String OUTPUT_SCHEMA_NAME = "secbrief_output";
    // max_output_tokens 는 추론 토큰까지 포함: 요청의 출력 상한에 더하는 추론 몫
    private static final int REASONING_TOKEN_HEADROOM = 2000;
    // status: incomplete 잘린 응답 / incomplete_details.reason: max_output_tokens 출력 상한, content_filter 안전 필터
    private static final String STATUS_INCOMPLETE = "incomplete";
    private static final String INCOMPLETE_REASON_MAXIMUM_OUTPUT_TOKENS = "max_output_tokens";
    private static final String INCOMPLETE_REASON_CONTENT_FILTER = "content_filter";
    // output[].type: message 만 읽음 (reasoning 등은 건너뜀) / content[].type: output_text 답, refusal 거부
    private static final String OUTPUT_TYPE_MESSAGE = "message";
    private static final String CONTENT_TYPE_OUTPUT_TEXT = "output_text";
    private static final String CONTENT_TYPE_REFUSAL = "refusal";

    private final AiApiKeyCallRunner aiApiKeyCallRunner;
    private final AiProviderModelService aiProviderModelService;
    private final SystemConfigService systemConfigService;
    private final ProviderHttpClient providerHttpClient;
    private final String responsesUrl;

    public OpenAiStructuredCompletionClient(AiApiKeyCallRunner aiApiKeyCallRunner,
                                            AiProviderModelService aiProviderModelService,
                                            SystemConfigService systemConfigService,
                                            ProviderHttpClient providerHttpClient,
                                            @Value("${secbrief.ai.openai.base-url:https://api.openai.com}") String baseUrl) {
        this.aiApiKeyCallRunner = aiApiKeyCallRunner;
        this.aiProviderModelService = aiProviderModelService;
        this.systemConfigService = systemConfigService;
        this.providerHttpClient = providerHttpClient;
        this.responsesUrl = ProviderHttpClient.toEndpointUrl(baseUrl, RESPONSES_PATH);
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isAvailable() {
        return aiApiKeyCallRunner.hasCallableKey(PROVIDER);
    }

    /**
     * 요청 본문 > 키 순회 > 거부·잘림 확인 > 텍스트(JSON). 호출 실패는 AI_API_ERROR (실패 종류만 메시지에 넣음)
     */
    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        String model = aiProviderModelService.findModel(PROVIDER);
        Map<String, Object> requestBody = toRequestBody(request, model);
        JsonNode responseBody = aiApiKeyCallRunner.call(new AiApiKeyCall<>(PROVIDER, model, request.callType(),
                key -> providerHttpClient.postJson(responsesUrl, headers -> headers.setBearerAuth(key.apiKey()), requestBody),
                OpenAiStructuredCompletionClient::toTokenUsage, ProviderHttpClient::classify));
        return toCompletion(responseBody);
    }

    // 본문: {model, instructions, input, max_output_tokens, reasoning:{effort}, store:false, text:{format:{type, name, strict, schema}}}
    private Map<String, Object> toRequestBody(StructuredCompletionRequest request, String model) {
        Map<String, Object> outputFormat = new LinkedHashMap<>();
        outputFormat.put("type", "json_schema");
        outputFormat.put("name", OUTPUT_SCHEMA_NAME);
        outputFormat.put("strict", true);
        outputFormat.put("schema", OpenAiJsonSchemaConverter.convert(request.outputJsonSchema()));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("instructions", request.systemInstruction());
        requestBody.put("input", request.userMessage());
        requestBody.put("max_output_tokens", request.maximumOutputTokens() + REASONING_TOKEN_HEADROOM);
        requestBody.put("reasoning", Map.of("effort", systemConfigService.getString(ConfigKey.OPENAI_REASONING_EFFORT)));
        requestBody.put("store", false);
        requestBody.put("text", Map.of("format", outputFormat));
        return requestBody;
    }

    // 응답 > 결과: 거부(refusal 조각·안전 필터로 멈춤) > 답 글자 이어 붙이기 + 출력 상한에 걸렸는지
    private static StructuredCompletion toCompletion(JsonNode responseBody) {
        AiTokenUsage tokenUsage = toTokenUsage(responseBody);
        String incompleteReason = STATUS_INCOMPLETE.equals(responseBody.path("status").asText(""))
                ? responseBody.path("incomplete_details").path("reason").asText("")
                : "";
        StringBuilder responseJson = new StringBuilder();
        boolean hasRefusal = INCOMPLETE_REASON_CONTENT_FILTER.equals(incompleteReason);
        for (JsonNode outputItem : responseBody.path("output")) {
            if (!OUTPUT_TYPE_MESSAGE.equals(outputItem.path("type").asText(""))) {
                continue;
            }
            for (JsonNode contentItem : outputItem.path("content")) {
                String contentType = contentItem.path("type").asText("");
                hasRefusal = hasRefusal || CONTENT_TYPE_REFUSAL.equals(contentType);
                if (CONTENT_TYPE_OUTPUT_TEXT.equals(contentType)) {
                    responseJson.append(contentItem.path("text").asText(""));
                }
            }
        }
        if (hasRefusal) {
            return StructuredCompletion.refused(tokenUsage);
        }
        return StructuredCompletion.of(responseJson.toString(),
                INCOMPLETE_REASON_MAXIMUM_OUTPUT_TOKENS.equals(incompleteReason), tokenUsage);
    }

    // usage.output_tokens 는 추론 토큰을 포함한 값 (청구 기준)
    private static AiTokenUsage toTokenUsage(JsonNode responseBody) {
        JsonNode usage = responseBody.path("usage");
        return new AiTokenUsage(ProviderHttpClient.toTokenCount(usage.path("input_tokens")), ProviderHttpClient.toTokenCount(usage.path("output_tokens")));
    }
}
