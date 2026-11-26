package kr.suhsaechan.secbrief.advisory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.FakeProviderServer.StubResponse;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKey;
import kr.suhsaechan.secbrief.advisory.ai.schema.OpenAiJsonSchemaConverter;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * OpenAI 제공자 (로컬 가짜 서버, 외부 호출 없음): /v1/responses 요청 모양(strict 스키마·출력 상한·추론 노력), 응답 읽기(잘림·거부·사용량),
 * 키 우선순위·다음 키 넘김·쿨다운, 키별 호출 기록(제공자·모델·별명), 설정 저장소의 모델 반영
 */
class OpenAiStructuredCompletionClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL = "test-openai-model";
    private static final String FIRST_API_KEY = "sk-test-openai-first-0000000-AAAA";
    private static final String SECOND_API_KEY = "sk-test-openai-second-000000-BBBB";
    private static final AiApiKey FIRST_KEY = new AiApiKey("key-1", "오픈 운영", FIRST_API_KEY, true);
    private static final AiApiKey SECOND_KEY = new AiApiKey("key-2", "오픈 예비", SECOND_API_KEY, true);
    private static final List<ArticleTitleAnalysisRequest> TITLE_REQUESTS = List.of(
            new ArticleTitleAnalysisRequest("Critical flaw in Example VPN", ArticleRegion.OVERSEAS));
    private static final StructuredCompletionRequest REQUEST = new StructuredCompletionRequest(
            AiCallType.TITLE_ANALYSIS, ArticleTitleAnalysisPrompt.SYSTEM_INSTRUCTION,
            ArticleTitleAnalysisPrompt.toUserMessage(TITLE_REQUESTS), ArticleTitleAnalysisPrompt.toOutputJsonSchema(), 256);
    // max_output_tokens = 요청 상한 256 + 추론 몫 2000
    private static final int EXPECTED_MAXIMUM_OUTPUT_TOKENS = 2256;

    private final KeyedProviderTestFixture fixture = new KeyedProviderTestFixture(KeyedAiProvider.OPENAI);
    private FakeProviderServer server;
    private OpenAiStructuredCompletionClient client;

    @BeforeEach
    void startServerAndClient() {
        server = new FakeProviderServer("/v1/responses");
        server.respond(200, toResponse("completed", null, toMessageItem(toOutputText("{\"results\":[]}")), 812, 96));
        when(fixture.systemConfigService.getString(ConfigKey.OPENAI_MODEL)).thenReturn(MODEL);
        when(fixture.systemConfigService.getString(ConfigKey.OPENAI_REASONING_EFFORT)).thenReturn("none");
        fixture.givenCallableKeys(FIRST_KEY);
        client = createClient(server.baseUrl());
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void sendsResponsesRequestWithStrictSchemaAndReturnsJsonWithUsage() throws Exception {
        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion).isEqualTo(StructuredCompletion.of("{\"results\":[]}", false, new AiTokenUsage(812, 96)));
        assertThat(server.lastRequest().method()).isEqualTo("POST");
        assertThat(server.lastRequest().path()).isEqualTo("/v1/responses");
        assertThat(server.lastRequest().headers().getFirst("Authorization")).isEqualTo("Bearer " + FIRST_API_KEY);
        JsonNode requestBody = OBJECT_MAPPER.readTree(server.lastRequest().body());
        assertThat(requestBody.get("model").asText()).isEqualTo(MODEL);
        assertThat(requestBody.get("instructions").asText()).isEqualTo(REQUEST.systemInstruction());
        assertThat(requestBody.get("input").asText()).isEqualTo(REQUEST.userMessage());
        assertThat(requestBody.get("max_output_tokens").asInt()).isEqualTo(EXPECTED_MAXIMUM_OUTPUT_TOKENS);
        assertThat(requestBody.get("reasoning").get("effort").asText()).isEqualTo("none");
        assertThat(requestBody.get("store").asBoolean()).isFalse();
        JsonNode outputFormat = requestBody.get("text").get("format");
        assertThat(outputFormat.get("type").asText()).isEqualTo("json_schema");
        assertThat(outputFormat.get("name").asText()).isEqualTo("secbrief_output");
        assertThat(outputFormat.get("strict").asBoolean()).isTrue();
        assertThat(outputFormat.get("schema")).isEqualTo(OBJECT_MAPPER.valueToTree(
                OpenAiJsonSchemaConverter.convert(ArticleTitleAnalysisPrompt.toOutputJsonSchema())));
    }

    @Test
    void joinsOutputTextOfMessageItemsAndSkipsReasoningItems() {
        server.respond(200, toResponse("completed", null,
                List.of(Map.of("type", "reasoning", "summary", List.of(Map.of("type", "summary_text", "text", "thinking"))),
                        Map.of("type", "message", "role", "assistant", "status", "completed",
                                "content", List.of(toOutputText("{\"results\":"), toOutputText("[]}")))), 10, 20));

        assertThat(client.requestCompletion(REQUEST).responseJson()).isEqualTo("{\"results\":[]}");
    }

    @Test
    void marksCompletionTruncatedWhenStoppedByMaximumOutputTokens() {
        server.respond(200, toResponse("incomplete", "max_output_tokens", toMessageItem(toOutputText("{\"results\":[")), 30, 2256));

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion.isTruncated()).isTrue();
        assertThat(completion.isRefused()).isFalse();
        assertThat(completion.responseJson()).isEqualTo("{\"results\":[");
        assertThat(completion.tokenUsage()).isEqualTo(new AiTokenUsage(30, 2256));
    }

    @Test
    void returnsRefusedForRefusalContentAndContentFilter() {
        server.respond(200, toResponse("completed", null,
                toMessageItem(Map.of("type", "refusal", "refusal", "I'm sorry, I cannot assist with that request.")), 30, 12));
        StructuredCompletion refusedCompletion = client.requestCompletion(REQUEST);
        assertThat(refusedCompletion.isRefused()).isTrue();
        assertThat(refusedCompletion.responseJson()).isNull();
        assertThat(refusedCompletion.tokenUsage()).isEqualTo(new AiTokenUsage(30, 12));

        // 안전 필터로 멈춘 응답: 잘림이 아니라 거부
        server.respond(200, toResponse("incomplete", "content_filter", toMessageItem(toOutputText("{\"res")), 30, 4));
        StructuredCompletion filteredCompletion = client.requestCompletion(REQUEST);
        assertThat(filteredCompletion.isRefused()).isTrue();
        assertThat(filteredCompletion.isTruncated()).isFalse();
    }

    @Test
    void returnsEmptyResponseWhenBodyHasNoOutputAndUsage() {
        server.respond(200, "{\"id\":\"resp_1\",\"status\":\"completed\"}");

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        // 호출은 성공(2xx): 예외가 아니라 빈 응답 (호출부가 건별 실패로 처리)
        assertThat(completion.responseJson()).isEmpty();
        assertThat(completion.tokenUsage().isKnown()).isFalse();
    }

    @Test
    void recordsOneCallLogWithProviderModelAndKeyNickname() {
        client.requestCompletion(REQUEST);

        ArgumentCaptor<AiCallAttempt> attempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        verify(fixture.aiCallLogService).recordSuccess(attempt.capture(), eq(new AiTokenUsage(812, 96)));
        assertThat(attempt.getValue().callType()).isEqualTo(AiCallType.TITLE_ANALYSIS);
        assertThat(attempt.getValue().providerCode()).isEqualTo("openai");
        assertThat(attempt.getValue().model()).isEqualTo(MODEL);
        assertThat(attempt.getValue().keyLabel()).isEqualTo("오픈 운영");
        assertThat(attempt.getValue().latencyMilliseconds()).isNotNegative();
        assertThat(attempt.getValue().toString()).doesNotContain(FIRST_API_KEY);
    }

    @Test
    void movesToNextKeyOnUnauthorizedAndCoolsDownFailedKey() {
        fixture.givenCallableKeys(FIRST_KEY, SECOND_KEY);
        respondByKey(new StubResponse(401, "{\"error\":{\"message\":\"Incorrect API key provided\",\"type\":\"invalid_request_error\"}}"),
                new StubResponse(200, toResponse("completed", null, toMessageItem(toOutputText("{}")), 10, 5)));

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion.responseJson()).isEqualTo("{}");
        ArgumentCaptor<AiCallAttempt> failedAttempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        ArgumentCaptor<AiCallAttempt> succeededAttempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        InOrder callLogOrder = inOrder(fixture.aiCallLogService);
        callLogOrder.verify(fixture.aiCallLogService).recordFailure(failedAttempt.capture(), eq(AiFailureKind.UNAUTHORIZED));
        callLogOrder.verify(fixture.aiCallLogService).recordSuccess(succeededAttempt.capture(), any());
        assertThat(failedAttempt.getValue().keyLabel()).isEqualTo("오픈 운영");
        assertThat(succeededAttempt.getValue().keyLabel()).isEqualTo("오픈 예비");
        assertThat(fixture.tracker.isCoolingDown("key-1")).isTrue();
        assertThat(fixture.tracker.isCoolingDown("key-2")).isFalse();

        // 다음 호출: 쿨다운 중인 첫째 키는 뒤로, 둘째 키만 부름
        client.requestCompletion(REQUEST);
        assertThat(server.receivedRequests()).extracting(request -> request.headers().getFirst("Authorization"))
                .containsExactly("Bearer " + FIRST_API_KEY, "Bearer " + SECOND_API_KEY, "Bearer " + SECOND_API_KEY);
    }

    @Test
    void recordsConnectionFailureWithoutLeakingAddressOrKey() {
        server.close();

        assertThatThrownBy(() -> client.requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class)
                .hasMessageNotContaining("127.0.0.1")
                .hasMessageNotContaining("sk-test")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        verify(fixture.aiCallLogService).recordFailure(any(), eq(AiFailureKind.CONNECTION_FAILED));
    }

    @Test
    void isUnavailableAndDoesNotCallWithoutKeys() {
        fixture.givenCallableKeys();

        assertThat(client.getProviderCode()).isEqualTo("openai");
        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("provider not configured");
        assertThat(server.receivedRequests()).isEmpty();
    }

    @Test
    void readsModelAndReasoningEffortFromConfigStoreOnEveryCall() throws Exception {
        client.requestCompletion(REQUEST);
        when(fixture.systemConfigService.getString(ConfigKey.OPENAI_MODEL)).thenReturn("changed-model");
        when(fixture.systemConfigService.getString(ConfigKey.OPENAI_REASONING_EFFORT)).thenReturn("low");

        client.requestCompletion(REQUEST);

        JsonNode requestBody = OBJECT_MAPPER.readTree(server.lastRequest().body());
        assertThat(requestBody.get("model").asText()).isEqualTo("changed-model");
        assertThat(requestBody.get("reasoning").get("effort").asText()).isEqualTo("low");
    }

    @Test
    void worksInsideTitleAnalysisFlowAndAcceptsBaseUrlWithTrailingSlash() {
        server.respond(200, toResponse("completed", null, toMessageItem(toOutputText(
                "{\"results\":[{\"number\":1,\"topic\":\"remoteAccess\",\"translatedTitle\":\"Example VPN 심각한 결함\"}]}")), 50, 30));
        ArticleTitleAnalyzer analyzer = new StructuredOutputArticleTitleAnalyzer(createClient(server.baseUrl() + "/"));

        List<ArticleTitleAnalysisOutcome> outcomes = analyzer.analyzeBatch(TITLE_REQUESTS);

        assertThat(outcomes).hasSize(1).allMatch(ArticleTitleAnalysisOutcome::isSuccessful);
        assertThat(server.lastRequest().path()).isEqualTo("/v1/responses");
    }

    private OpenAiStructuredCompletionClient createClient(String baseUrl) {
        return new OpenAiStructuredCompletionClient(fixture.createCallRunner(), fixture.createModelService(),
                fixture.systemConfigService, new ProviderHttpClient(), baseUrl);
    }

    // 첫째 키·둘째 키에 서로 다른 응답
    private void respondByKey(StubResponse firstKeyResponse, StubResponse secondKeyResponse) {
        server.respondBy(request -> ("Bearer " + FIRST_API_KEY).equals(request.headers().getFirst("Authorization"))
                ? firstKeyResponse
                : secondKeyResponse);
    }

    private static List<Map<String, Object>> toMessageItem(Map<String, Object> contentItem) {
        return List.of(Map.of("type", "message", "role", "assistant", "status", "completed", "content", List.of(contentItem)));
    }

    private static Map<String, Object> toOutputText(String text) {
        return Map.of("type", "output_text", "text", text, "annotations", List.of());
    }

    // Responses API 응답 모양: status, incomplete_details.reason, output[], usage
    private static String toResponse(String status, String incompleteReason, List<Map<String, Object>> outputItems,
                                     int inputTokens, int outputTokens) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("id", "resp_test");
        response.put("object", "response");
        response.put("status", status);
        response.put("incomplete_details", incompleteReason == null ? null : Map.of("reason", incompleteReason));
        response.put("output", outputItems);
        response.put("usage", Map.of("input_tokens", inputTokens, "output_tokens", outputTokens,
                "output_tokens_details", Map.of("reasoning_tokens", 0), "total_tokens", inputTokens + outputTokens));
        try {
            return OBJECT_MAPPER.writeValueAsString(response);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
