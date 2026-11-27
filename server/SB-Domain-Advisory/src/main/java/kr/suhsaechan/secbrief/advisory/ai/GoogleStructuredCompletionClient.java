package kr.suhsaechan.secbrief.advisory.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKey;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyCall;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyCallRunner;
import kr.suhsaechan.secbrief.advisory.ai.schema.GoogleJsonSchemaConverter;
import kr.suhsaechan.secbrief.advisory.constant.AiProviderCode;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.json.JsonSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google(Gemini) 제공자: 요청 1회 > 키 우선순위대로 POST {base-url}/v1beta/interactions (response_format 에 JSON 스키마) > 응답 JSON 문자열
 * 키 순회·키마다 호출 기록 1행은 AiApiKeyCallRunner, 쓸 키가 없으면 호출하지 않음. 대화는 저장하지 않게 요청 (store false)
 */
@Component
public class GoogleStructuredCompletionClient implements StructuredCompletionClient {

    public static final String PROVIDER_CODE = AiProviderCode.GOOGLE;
    private static final KeyedAiProvider PROVIDER = KeyedAiProvider.GOOGLE;

    private static final String INTERACTIONS_PATH = "/v1beta/interactions";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    // max_output_tokens 는 생각 토큰까지 포함: 요청의 출력 상한에 더하는 생각 몫
    private static final int THINKING_TOKEN_HEADROOM = 2000;
    // status: incomplete 출력 상한 등으로 덜 끝난 응답
    private static final String STATUS_INCOMPLETE = "incomplete";
    // steps[].type: model_output 만 읽음 (thought 등은 건너뜀) / content[].type: text
    private static final String STEP_TYPE_MODEL_OUTPUT = "model_output";
    private static final String CONTENT_TYPE_TEXT = "text";
    // 오류 코드: 입력·출력이 안전 필터 등에 막힘 (400, 입력 내용 문제라 거부로 다룸)
    private static final Set<String> GENERATION_BLOCKED_ERROR_CODES = Set.of("safety", "jailbreak", "recitation",
            "language", "prohibited_content", "spii", "blocklist", "content_blocked");
    // 오류 코드: 결제 미설정 등 선행 조건 미충족 (400, 다른 프로젝트의 키면 될 수 있어 다음 키로 넘김)
    private static final String FAILED_PRECONDITION_ERROR_CODE = "failed_precondition";

    private final AiApiKeyCallRunner aiApiKeyCallRunner;
    private final AiProviderModelService aiProviderModelService;
    private final SystemConfigService systemConfigService;
    private final ProviderHttpClient providerHttpClient;
    private final String interactionsUrl;

    public GoogleStructuredCompletionClient(AiApiKeyCallRunner aiApiKeyCallRunner,
                                            AiProviderModelService aiProviderModelService,
                                            SystemConfigService systemConfigService,
                                            ProviderHttpClient providerHttpClient,
                                            @Value("${secbrief.ai.google.base-url}")
                                            String baseUrl) {
        this.aiApiKeyCallRunner = aiApiKeyCallRunner;
        this.aiProviderModelService = aiProviderModelService;
        this.systemConfigService = systemConfigService;
        this.providerHttpClient = providerHttpClient;
        this.interactionsUrl = ProviderHttpClient.toEndpointUrl(baseUrl, INTERACTIONS_PATH);
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
     * 요청 본문 > 키 순회 > 차단·잘림 확인 > 텍스트(JSON). 호출 실패는 AI_API_ERROR (실패 종류만 메시지에 넣음)
     */
    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        String model = aiProviderModelService.findModel(PROVIDER);
        Map<String, Object> requestBody = toRequestBody(request, model);
        JsonNode responseBody = aiApiKeyCallRunner.call(new AiApiKeyCall<>(PROVIDER, model, request.callType(),
                key -> requestInteraction(key, requestBody),
                GoogleStructuredCompletionClient::toTokenUsage, GoogleStructuredCompletionClient::classifyFailure));
        return toCompletion(responseBody);
    }

    // 키 1개로 호출 1회. 안전 필터 차단(400)은 호출 실패가 아니라 오류 본문을 응답으로 돌려줌 (키 탓이 아님)
    private JsonNode requestInteraction(AiApiKey key, Map<String, Object> requestBody) {
        try {
            return providerHttpClient.postJson(interactionsUrl,
                    headers -> headers.set(API_KEY_HEADER, key.apiKey()), requestBody);
        } catch (RestClientResponseException exception) {
            JsonNode errorBody = readErrorBody(exception);
            if (isGenerationBlocked(errorBody)) {
                return errorBody;
            }
            throw exception;
        }
    }

    // 본문: {model, system_instruction, input, generation_config:{max_output_tokens, thinking_level}, response_format:{type, mime_type, schema}, store:false}
    private Map<String, Object> toRequestBody(StructuredCompletionRequest request, String model) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("max_output_tokens", request.maximumOutputTokens() + THINKING_TOKEN_HEADROOM);
        generationConfig.put("thinking_level", systemConfigService.getString(ConfigKey.GOOGLE_THINKING_LEVEL));

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "text");
        responseFormat.put("mime_type", "application/json");
        responseFormat.put("schema", GoogleJsonSchemaConverter.convert(request.outputJsonSchema()));

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("system_instruction", request.systemInstruction());
        requestBody.put("input", request.userMessage());
        requestBody.put("generation_config", generationConfig);
        requestBody.put("response_format", responseFormat);
        requestBody.put("store", false);
        return requestBody;
    }

    // 응답 > 결과: 차단이면 거부 > model_output 의 text 이어 붙이기 + 덜 끝났는지(incomplete)
    private static StructuredCompletion toCompletion(JsonNode responseBody) {
        AiTokenUsage tokenUsage = toTokenUsage(responseBody);
        if (isGenerationBlocked(responseBody)) {
            return StructuredCompletion.refused(tokenUsage);
        }
        StringBuilder responseJson = new StringBuilder();
        for (JsonNode step : responseBody.path("steps")) {
            if (!STEP_TYPE_MODEL_OUTPUT.equals(step.path("type").asText(""))) {
                continue;
            }
            for (JsonNode contentItem : step.path("content")) {
                if (CONTENT_TYPE_TEXT.equals(contentItem.path("type").asText(""))) {
                    responseJson.append(contentItem.path("text").asText(""));
                }
            }
        }
        return StructuredCompletion.of(responseJson.toString(),
                STATUS_INCOMPLETE.equals(responseBody.path("status").asText("")), tokenUsage);
    }

    // 차단 코드 위치 2곳: 오류 응답 error.code, 실패한 응답의 errors[].code
    private static boolean isGenerationBlocked(JsonNode responseBody) {
        if (GENERATION_BLOCKED_ERROR_CODES.contains(responseBody.path("error").path("code").asText(""))) {
            return true;
        }
        for (JsonNode error : responseBody.path("errors")) {
            if (GENERATION_BLOCKED_ERROR_CODES.contains(error.path("code").asText(""))) {
                return true;
            }
        }
        return false;
    }

    // 예외 > 실패 종류: 400 failed_precondition 은 결제 필요로 (다음 키), 그 밖은 상태 코드 기준
    private static AiFailureKind classifyFailure(RuntimeException failure) {
        boolean isFailedPrecondition = failure instanceof RestClientResponseException responseException
                && responseException.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
                && FAILED_PRECONDITION_ERROR_CODE.equals(readErrorBody(responseException).path("error").path("code").asText(""));
        return isFailedPrecondition ? AiFailureKind.PAYMENT_REQUIRED : ProviderHttpClient.classify(failure);
    }

    // 오류 본문 JSON (판정에만 쓰고 어디에도 남기지 않음, JSON 이 아니면 빈 노드)
    private static JsonNode readErrorBody(RestClientResponseException responseException) {
        try {
            JsonNode errorBody = JsonSupport.OBJECT_MAPPER.readTree(responseException.getResponseBodyAsString());
            return errorBody == null ? MissingNode.getInstance() : errorBody;
        } catch (JsonProcessingException exception) {
            return MissingNode.getInstance();
        }
    }

    // 출력 토큰 = 답(total_output_tokens) + 생각(total_thought_tokens): 생각 토큰도 출력 단가로 청구됨
    private static AiTokenUsage toTokenUsage(JsonNode responseBody) {
        JsonNode usage = responseBody.path("usage");
        Integer answerTokens = ProviderHttpClient.toTokenCount(usage.path("total_output_tokens"));
        Integer thoughtTokens = ProviderHttpClient.toTokenCount(usage.path("total_thought_tokens"));
        Integer outputTokens = answerTokens == null ? thoughtTokens
                : thoughtTokens == null ? answerTokens : Integer.valueOf(answerTokens + thoughtTokens);
        return new AiTokenUsage(ProviderHttpClient.toTokenCount(usage.path("total_input_tokens")), outputTokens);
    }
}
