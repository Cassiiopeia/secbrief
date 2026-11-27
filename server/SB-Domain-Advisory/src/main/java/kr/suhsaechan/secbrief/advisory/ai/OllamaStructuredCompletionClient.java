package kr.suhsaechan.secbrief.advisory.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.constant.AiProviderCode;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Ollama 제공자: 요청 1회 > POST {base-url}/api/chat (format 에 JSON 스키마) > message.content(JSON 문자열), 호출마다 호출 기록 1행
 * 주소나 모델이 비어 있으면 호출하지 않음 (서버 기동·수집에 영향 없음), 주소·키는 로그·예외에 넣지 않음
 */
@Component
public class OllamaStructuredCompletionClient implements StructuredCompletionClient {

    public static final String PROVIDER_CODE = AiProviderCode.OLLAMA;

    private static final String CHAT_PATH = "/api/chat";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    // 자체 서버 모델은 요청 1회 응답이 느릴 수 있음 (스케줄러 틱 주기 안쪽)
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    // 분류·번역·요약은 매번 같은 결과가 나오게
    private static final int TEMPERATURE = 0;
    // done_reason: 출력 토큰 상한(num_predict)에 걸려 멈춤
    private static final String DONE_REASON_LENGTH = "length";
    // 응답의 토큰 수 필드: 입력(prompt_eval_count), 출력(eval_count)
    private static final String INPUT_TOKENS_FIELD = "prompt_eval_count";
    private static final String OUTPUT_TOKENS_FIELD = "eval_count";

    private final AiCallLogService aiCallLogService;
    private final RestClient restClient;
    private final String chatUrl;
    private final String model;
    private final String apiKey;

    public OllamaStructuredCompletionClient(AiCallLogService aiCallLogService,
                                      @Value("${secbrief.ai.ollama.base-url}") String baseUrl,
                                      @Value("${secbrief.ai.ollama.model}") String model,
                                      @Value("${secbrief.ai.ollama.api-key}") String apiKey) {
        this.aiCallLogService = aiCallLogService;
        // 읽기 제한은 본문을 받는 중에도 적용 (멈춘 응답이 스케줄러 스레드를 붙잡지 않게)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.chatUrl = ProviderHttpClient.toEndpointUrlOrEmpty(baseUrl, CHAT_PATH);
        this.model = model == null ? "" : model.strip();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    @Override
    public String getProviderCode() {
        return PROVIDER_CODE;
    }

    @Override
    public boolean isAvailable() {
        return !chatUrl.isEmpty() && !model.isEmpty();
    }

    /**
     * 요청 > 2xx 확인 > message.content. 연결 실패·시간 초과·2xx 아님은 AI_API_ERROR (호출 실패)
     * 2xx 인데 내용이 없으면 빈 응답으로 돌려줌 (모델이 답을 못 한 것이라 호출부가 건별 실패로 처리)
     */
    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        if (!isAvailable()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "provider not configured");
        }
        JsonNode responseBody = requestChat(request);
        AiTokenUsage tokenUsage = toTokenUsage(responseBody);
        JsonNode content = responseBody == null ? null : responseBody.path("message").path("content");
        if (content == null || !content.isTextual()) {
            return StructuredCompletion.of("", false, tokenUsage);
        }
        return StructuredCompletion.of(content.asText(),
                DONE_REASON_LENGTH.equals(responseBody.path("done_reason").asText("")), tokenUsage);
    }

    // 호출 1회 + 호출 기록 (성공·실패 공통)
    private JsonNode requestChat(StructuredCompletionRequest request) {
        long startedAtNanoseconds = System.nanoTime();
        try {
            JsonNode responseBody = restClient.post()
                    .uri(URI.create(chatUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::addAuthorization)
                    .body(toChatRequestBody(request))
                    .retrieve()
                    .body(JsonNode.class);
            aiCallLogService.recordSuccess(toAttempt(request.callType(), startedAtNanoseconds), toTokenUsage(responseBody));
            return responseBody;
        } catch (IllegalArgumentException exception) {
            // 주소 형식 오류 (설정 문제): 주소는 남기지 않음
            aiCallLogService.recordFailure(toAttempt(request.callType(), startedAtNanoseconds),
                    AiFailureKind.INVALID_CONFIGURATION);
            throw new CustomException(ErrorCode.AI_API_ERROR, "invalid base url");
        } catch (RestClientResponseException exception) {
            int statusCode = exception.getStatusCode().value();
            aiCallLogService.recordFailure(toAttempt(request.callType(), startedAtNanoseconds),
                    AiFailureKind.fromStatusCode(statusCode, null));
            throw new CustomException(ErrorCode.AI_API_ERROR, "status=" + statusCode);
        } catch (RestClientException exception) {
            // 연결 실패·시간 초과·JSON 아님: 원인 종류만 남김
            aiCallLogService.recordFailure(toAttempt(request.callType(), startedAtNanoseconds),
                    AiFailureKind.CONNECTION_FAILED);
            throw new CustomException(ErrorCode.AI_API_ERROR,
                    "unreachable : " + exception.getMostSpecificCause().getClass().getSimpleName());
        }
    }

    // 키 별명 없음 (인증 프록시 키는 1개뿐이라 구분하지 않음)
    private AiCallAttempt toAttempt(AiCallType callType, long startedAtNanoseconds) {
        return new AiCallAttempt(callType, PROVIDER_CODE, model, null,
                Duration.ofNanos(System.nanoTime() - startedAtNanoseconds).toMillis());
    }

    // 응답에 토큰 수가 없으면(옛 버전·프록시) 모름
    private static AiTokenUsage toTokenUsage(JsonNode responseBody) {
        if (responseBody == null) {
            return AiTokenUsage.UNKNOWN;
        }
        return new AiTokenUsage(ProviderHttpClient.toTokenCount(responseBody.path(INPUT_TOKENS_FIELD)),
                ProviderHttpClient.toTokenCount(responseBody.path(OUTPUT_TOKENS_FIELD)));
    }

    // 인증 헤더: 키가 있을 때만 (인증 프록시 뒤의 Ollama)
    private void addAuthorization(HttpHeaders headers) {
        if (!apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }
    }

    // 본문: {model, messages:[system, user], stream:false, format:<JSON 스키마>, options:{temperature, num_predict}}
    private Map<String, Object> toChatRequestBody(StructuredCompletionRequest request) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", TEMPERATURE);
        // num_predict: 출력 토큰 상한 (끝없이 생성하는 모델 방지)
        options.put("num_predict", request.maximumOutputTokens());

        Map<String, Object> chatRequestBody = new LinkedHashMap<>();
        chatRequestBody.put("model", model);
        chatRequestBody.put("messages", List.of(
                Map.of("role", "system", "content", request.systemInstruction()),
                Map.of("role", "user", "content", request.userMessage())));
        chatRequestBody.put("stream", false);
        chatRequestBody.put("format", request.outputJsonSchema());
        chatRequestBody.put("options", options);
        return chatRequestBody;
    }
}
