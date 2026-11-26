package kr.suhsaechan.secbrief.advisory.ai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisOutcome.failed;
import static kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisOutcome.succeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Ollama 제공자 (제목 분석 흐름에 끼워 확인): /api/chat 요청 본문·인증 헤더, message.content > 건별 결과,
 * 미설정·2xx 아님·연결 실패·빈 응답 처리, 토큰 수(prompt_eval_count·eval_count)와 호출 기록 (로컬 서버, 외부 호출 없음)
 */
class OllamaStructuredCompletionClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL = "test-model";
    private static final List<ArticleTitleAnalysisRequest> REQUESTS = List.of(
            new ArticleTitleAnalysisRequest("Critical flaw in Example VPN", ArticleRegion.OVERSEAS),
            new ArticleTitleAnalysisRequest("개인정보 유출 사고", ArticleRegion.DOMESTIC));
    private static final String FIRST_RESULT = "{\"number\":1,\"topic\":\"remoteAccess\",\"translatedTitle\":\"Example VPN 심각한 결함\"}";
    private static final String SECOND_RESULT = "{\"number\":2,\"topic\":\"privacy\",\"translatedTitle\":null}";
    private static final ArticleTitleAnalysisOutcome FIRST_OUTCOME =
            succeeded(new ArticleTitleAnalysis(ArticleTopic.REMOTE_ACCESS, "Example VPN 심각한 결함", ArticleImportance.ROUTINE, null));
    private static final ArticleTitleAnalysisOutcome SECOND_OUTCOME =
            succeeded(new ArticleTitleAnalysis(ArticleTopic.PRIVACY, null, ArticleImportance.ROUTINE, null));

    // 호출 기록은 가짜 (DB 없이 무엇을 기록하려 했는지만 확인)
    private final AiCallLogService aiCallLogService = mock(AiCallLogService.class);
    private final AtomicReference<Headers> receivedHeaders = new AtomicReference<>();
    private final AtomicReference<String> receivedMethod = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/chat", exchange -> {
            receivedHeaders.set(exchange.getRequestHeaders());
            receivedMethod.set(exchange.getRequestMethod());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            respond(exchange, responseStatus.get(), responseBody.get());
        });
        server.start();
        responseBody.set(toChatResponse("{\"results\":[" + FIRST_RESULT + "," + SECOND_RESULT + "]}", "stop"));
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsChatRequestWithSchemaAndReturnsOutcomes() throws Exception {
        List<ArticleTitleAnalysisOutcome> outcomes = createAnalyzer("").analyzeBatch(REQUESTS);

        assertThat(outcomes).containsExactly(FIRST_OUTCOME, SECOND_OUTCOME);
        JsonNode chatRequest = OBJECT_MAPPER.readTree(receivedBody.get());
        assertThat(receivedMethod.get()).isEqualTo("POST");
        assertThat(chatRequest.get("model").asText()).isEqualTo(MODEL);
        assertThat(chatRequest.get("stream").asBoolean()).isFalse();
        assertThat(chatRequest.get("options").get("temperature").asInt()).isZero();
        assertThat(chatRequest.get("options").get("num_predict").asInt())
                .isEqualTo(ArticleTitleAnalysisPrompt.calculateMaximumOutputTokens(REQUESTS));
        assertThat(chatRequest.get("format"))
                .isEqualTo(OBJECT_MAPPER.valueToTree(ArticleTitleAnalysisPrompt.toOutputJsonSchema()));
        assertThat(chatRequest.get("messages")).hasSize(2);
        assertThat(chatRequest.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(chatRequest.get("messages").get(0).get("content").asText())
                .isEqualTo(ArticleTitleAnalysisPrompt.SYSTEM_INSTRUCTION);
        assertThat(chatRequest.get("messages").get(1).get("role").asText()).isEqualTo("user");
        assertThat(chatRequest.get("messages").get(1).get("content").asText())
                .isEqualTo(ArticleTitleAnalysisPrompt.toUserMessage(REQUESTS));
    }

    @Test
    void sendsBearerAuthorizationOnlyWhenApiKeyIsSet() {
        createAnalyzer("").analyzeBatch(REQUESTS);
        assertThat(receivedHeaders.get().getFirst("Authorization")).isNull();

        createAnalyzer("test-key").analyzeBatch(REQUESTS);
        assertThat(receivedHeaders.get().getFirst("Authorization")).isEqualTo("Bearer test-key");
    }

    @Test
    void acceptsBaseUrlWithTrailingSlash() {
        ArticleTitleAnalyzer analyzer = toAnalyzer(new OllamaStructuredCompletionClient(aiCallLogService, toLocalBaseUrl() + "/", MODEL, ""));

        assertThat(analyzer.analyzeBatch(REQUESTS)).containsExactly(FIRST_OUTCOME, SECOND_OUTCOME);
    }

    @Test
    void isUnavailableWithoutBaseUrlOrModel() {
        ArticleTitleAnalyzer analyzerWithoutBaseUrl = toAnalyzer(new OllamaStructuredCompletionClient(aiCallLogService, " ", MODEL, ""));
        ArticleTitleAnalyzer analyzerWithoutModel = toAnalyzer(new OllamaStructuredCompletionClient(aiCallLogService, toLocalBaseUrl(), "", ""));

        assertThat(analyzerWithoutBaseUrl.isAvailable()).isFalse();
        assertThat(analyzerWithoutModel.isAvailable()).isFalse();
        assertThat(createAnalyzer("").isAvailable()).isTrue();
        assertThatThrownBy(() -> analyzerWithoutBaseUrl.analyzeBatch(REQUESTS))
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(receivedBody.get()).isNull();
    }

    @Test
    void keepsCompleteResultsWhenStoppedByLength() {
        responseBody.set(toChatResponse("{\"results\":[" + FIRST_RESULT + ",{\"number\":2,\"top", "length"));

        assertThat(createAnalyzer("").analyzeBatch(REQUESTS))
                .containsExactly(FIRST_OUTCOME, failed("missing in response"));
    }

    @Test
    void throwsAiApiErrorWithStatusWhenNot2xx() {
        responseStatus.set(503);
        responseBody.set("{\"error\":\"busy\"}");

        assertThatThrownBy(() -> createAnalyzer("test-key").analyzeBatch(REQUESTS))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("status=503")
                .hasMessageNotContaining("test-key")
                .hasMessageNotContaining("127.0.0.1")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void failsEveryItemWhenMessageContentIsMissingOrNotJson() {
        // 호출은 성공(2xx)했지만 읽을 수 없는 응답: 예외가 아니라 건별 실패 (시도 횟수를 씀)
        responseBody.set("{\"done\":true}");
        assertThat(createAnalyzer("").analyzeBatch(REQUESTS))
                .hasSameSizeAs(REQUESTS).noneMatch(ArticleTitleAnalysisOutcome::isSuccessful);

        responseBody.set(toChatResponse("I cannot help with that", "stop"));
        assertThat(createAnalyzer("").analyzeBatch(REQUESTS))
                .hasSameSizeAs(REQUESTS).noneMatch(ArticleTitleAnalysisOutcome::isSuccessful);
    }

    @Test
    void throwsAiApiErrorWhenConnectionFailsOrBaseUrlIsMalformed() {
        ArticleTitleAnalyzer analyzer = createAnalyzer("");
        server.stop(0);

        assertThatThrownBy(() -> analyzer.analyzeBatch(REQUESTS))
                .isInstanceOf(CustomException.class)
                .hasMessageNotContaining("127.0.0.1")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThatThrownBy(() -> toAnalyzer(new OllamaStructuredCompletionClient(aiCallLogService, "http://127.0.0.1/bad path", MODEL, "")).analyzeBatch(REQUESTS))
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
    }

    @Test
    void returnsTokenUsageAndRecordsSuccessfulCall() {
        responseBody.set("{\"message\":{\"role\":\"assistant\",\"content\":\"{}\"},\"done\":true,"
                + "\"done_reason\":\"stop\",\"prompt_eval_count\":812,\"eval_count\":96}");
        OllamaStructuredCompletionClient client = new OllamaStructuredCompletionClient(aiCallLogService, toLocalBaseUrl(), MODEL, "test-key");

        StructuredCompletion completion = client.requestCompletion(
                new StructuredCompletionRequest(AiCallType.SUMMARY, "instruction", "{}", Map.of("type", "object"), 16));

        assertThat(completion.tokenUsage()).isEqualTo(new AiTokenUsage(812, 96));
        ArgumentCaptor<AiCallAttempt> attempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        verify(aiCallLogService).recordSuccess(attempt.capture(), eq(new AiTokenUsage(812, 96)));
        assertThat(attempt.getValue().callType()).isEqualTo(AiCallType.SUMMARY);
        assertThat(attempt.getValue().providerCode()).isEqualTo("ollama");
        assertThat(attempt.getValue().model()).isEqualTo(MODEL);
        // 인증 프록시 키는 기록하지 않음
        assertThat(attempt.getValue().keyLabel()).isNull();
        assertThat(attempt.getValue().latencyMilliseconds()).isNotNegative();
    }

    @Test
    void tokenUsageIsUnknownWhenResponseHasNoCounts() {
        StructuredCompletion completion = new OllamaStructuredCompletionClient(aiCallLogService, toLocalBaseUrl(), MODEL, "")
                .requestCompletion(new StructuredCompletionRequest(
                        AiCallType.TITLE_ANALYSIS, "instruction", "{}", Map.of("type", "object"), 16));

        assertThat(completion.tokenUsage().isKnown()).isFalse();
    }

    @Test
    void recordsFailureKindWhenCallFails() {
        responseStatus.set(503);
        responseBody.set("{\"error\":\"busy\"}");
        assertThatThrownBy(() -> createAnalyzer("").analyzeBatch(REQUESTS)).isInstanceOf(CustomException.class);
        verify(aiCallLogService).recordFailure(any(AiCallAttempt.class), eq(AiFailureKind.SERVER_ERROR));

        ArticleTitleAnalyzer analyzer = createAnalyzer("");
        server.stop(0);
        assertThatThrownBy(() -> analyzer.analyzeBatch(REQUESTS)).isInstanceOf(CustomException.class);
        verify(aiCallLogService).recordFailure(any(AiCallAttempt.class), eq(AiFailureKind.CONNECTION_FAILED));
        verify(aiCallLogService, never()).recordSuccess(any(), any());
    }

    private ArticleTitleAnalyzer createAnalyzer(String apiKey) {
        return toAnalyzer(new OllamaStructuredCompletionClient(aiCallLogService, toLocalBaseUrl(), MODEL, apiKey));
    }

    // 제목 분석 흐름 + 이 제공자 (운영과 같은 조합)
    private static ArticleTitleAnalyzer toAnalyzer(OllamaStructuredCompletionClient ollamaClient) {
        return new StructuredOutputArticleTitleAnalyzer(ollamaClient);
    }

    @Test
    void throwsAiApiErrorWithoutCallingWhenClientIsNotConfigured() {
        OllamaStructuredCompletionClient clientWithoutBaseUrl = new OllamaStructuredCompletionClient(aiCallLogService, "", MODEL, "");

        assertThatThrownBy(() -> clientWithoutBaseUrl.requestCompletion(
                new StructuredCompletionRequest(AiCallType.TITLE_ANALYSIS, "instruction", "{}", Map.of("type", "object"), 16)))
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(receivedBody.get()).isNull();
    }

    private String toLocalBaseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    // Ollama /api/chat 응답 모양: message.content 가 JSON 문자열
    private static String toChatResponse(String content, String doneReason) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "model", MODEL,
                    "message", Map.of("role", "assistant", "content", content),
                    "done", true,
                    "done_reason", doneReason));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bodyBytes.length);
        exchange.getResponseBody().write(bodyBytes);
        exchange.close();
    }
}
