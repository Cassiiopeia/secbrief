package kr.suhsaechan.secbrief.advisory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.FakeProviderServer.StubResponse;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKey;
import kr.suhsaechan.secbrief.advisory.ai.schema.GoogleJsonSchemaConverter;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Google(Gemini) 제공자 (로컬 가짜 서버, 외부 호출 없음): /v1beta/interactions 요청 모양(스키마·출력 상한·생각 수준), 응답 읽기(잘림·차단·사용량),
 * 키 우선순위·다음 키 넘김·쿨다운, 키별 호출 기록(제공자·모델·별명), 설정 저장소의 모델 반영
 */
class GoogleStructuredCompletionClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MODEL = "test-gemini-model";
    private static final String API_KEY_HEADER = "x-goog-api-key";
    private static final String FIRST_API_KEY = "test-google-key-first-000000-AAAA";
    private static final String SECOND_API_KEY = "test-google-key-second-00000-BBBB";
    private static final AiApiKey FIRST_KEY = new AiApiKey("key-1", "구글 운영", FIRST_API_KEY, true);
    private static final AiApiKey SECOND_KEY = new AiApiKey("key-2", "구글 예비", SECOND_API_KEY, true);
    private static final List<ArticleTitleAnalysisRequest> TITLE_REQUESTS = List.of(
            new ArticleTitleAnalysisRequest("Critical flaw in Example VPN", ArticleRegion.OVERSEAS));
    private static final StructuredCompletionRequest REQUEST = new StructuredCompletionRequest(
            AiCallType.SUMMARY, ArticleTitleAnalysisPrompt.SYSTEM_INSTRUCTION,
            ArticleTitleAnalysisPrompt.toUserMessage(TITLE_REQUESTS), ArticleTitleAnalysisPrompt.toOutputJsonSchema(), 256);
    // max_output_tokens = 요청 상한 256 + 생각 몫 2000
    private static final int EXPECTED_MAXIMUM_OUTPUT_TOKENS = 2256;

    private final KeyedProviderTestFixture fixture = new KeyedProviderTestFixture(KeyedAiProvider.GOOGLE);
    private FakeProviderServer server;
    private GoogleStructuredCompletionClient client;

    @BeforeEach
    void startServerAndClient() {
        server = new FakeProviderServer("/v1beta/interactions");
        server.respond(200, toInteraction("completed", "{\"results\":[]}", 812, 96, 0));
        when(fixture.systemConfigService.getString(ConfigKey.GOOGLE_MODEL)).thenReturn(MODEL);
        when(fixture.systemConfigService.getString(ConfigKey.GOOGLE_THINKING_LEVEL)).thenReturn("minimal");
        fixture.givenCallableKeys(FIRST_KEY);
        client = createClient(server.baseUrl());
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void sendsInteractionRequestWithSchemaAndReturnsJsonWithUsage() throws Exception {
        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion).isEqualTo(StructuredCompletion.of("{\"results\":[]}", false, new AiTokenUsage(812, 96)));
        assertThat(server.lastRequest().method()).isEqualTo("POST");
        assertThat(server.lastRequest().path()).isEqualTo("/v1beta/interactions");
        assertThat(server.lastRequest().headers().getFirst(API_KEY_HEADER)).isEqualTo(FIRST_API_KEY);
        // 키는 헤더로만 (주소·Authorization 에 넣지 않음)
        assertThat(server.lastRequest().headers().getFirst("Authorization")).isNull();
        JsonNode requestBody = OBJECT_MAPPER.readTree(server.lastRequest().body());
        assertThat(requestBody.get("model").asText()).isEqualTo(MODEL);
        assertThat(requestBody.get("system_instruction").asText()).isEqualTo(REQUEST.systemInstruction());
        assertThat(requestBody.get("input").asText()).isEqualTo(REQUEST.userMessage());
        assertThat(requestBody.get("generation_config").get("max_output_tokens").asInt()).isEqualTo(EXPECTED_MAXIMUM_OUTPUT_TOKENS);
        assertThat(requestBody.get("generation_config").get("thinking_level").asText()).isEqualTo("minimal");
        assertThat(requestBody.get("store").asBoolean()).isFalse();
        JsonNode responseFormat = requestBody.get("response_format");
        assertThat(responseFormat.get("type").asText()).isEqualTo("text");
        assertThat(responseFormat.get("mime_type").asText()).isEqualTo("application/json");
        assertThat(responseFormat.get("schema")).isEqualTo(OBJECT_MAPPER.valueToTree(
                GoogleJsonSchemaConverter.convert(ArticleTitleAnalysisPrompt.toOutputJsonSchema())));
    }

    @Test
    void joinsTextOfModelOutputStepsAndSkipsThoughtSteps() {
        server.respond(200, toJson(Map.of("id", "int_test", "status", "completed",
                "steps", List.of(
                        Map.of("type", "thought", "signature", "sig", "summary", List.of(Map.of("type", "text", "text", "thinking"))),
                        Map.of("type", "model_output", "content", List.of(
                                Map.of("type", "text", "text", "{\"results\":"), Map.of("type", "text", "text", "[]}")))))));

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion.responseJson()).isEqualTo("{\"results\":[]}");
        assertThat(completion.tokenUsage().isKnown()).isFalse();
    }

    @Test
    void countsThoughtTokensAsOutputTokens() {
        server.respond(200, toInteraction("completed", "{}", 100, 40, 25));

        // 생각 토큰도 출력 단가로 청구: 답 40 + 생각 25
        assertThat(client.requestCompletion(REQUEST).tokenUsage()).isEqualTo(new AiTokenUsage(100, 65));
    }

    @Test
    void marksCompletionTruncatedWhenInteractionIsIncomplete() {
        server.respond(200, toInteraction("incomplete", "{\"results\":[", 30, 2256, 0));

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion.isTruncated()).isTrue();
        assertThat(completion.isRefused()).isFalse();
        assertThat(completion.responseJson()).isEqualTo("{\"results\":[");
    }

    @Test
    void returnsRefusedWithoutBlamingKeyWhenSafetyFilterBlocksRequest() {
        fixture.givenCallableKeys(FIRST_KEY, SECOND_KEY);
        server.respond(400, "{\"error\":{\"code\":\"safety\",\"message\":\"Request blocked due to safety violations\"}}");

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        // 입력 내용 문제: 거부로 돌려주고 다음 키로 넘기지 않음, 키를 쉬게 하지 않음
        assertThat(completion.isRefused()).isTrue();
        assertThat(completion.responseJson()).isNull();
        assertThat(server.receivedRequests()).hasSize(1);
        assertThat(fixture.tracker.isCoolingDown("key-1")).isFalse();
        verify(fixture.aiCallLogService, never()).recordFailure(any(), any());
    }

    @Test
    void returnsRefusedWhenFailedInteractionCarriesBlockedErrorCode() {
        server.respond(200, "{\"id\":\"int_test\",\"status\":\"failed\",\"steps\":[],"
                + "\"errors\":[{\"code\":\"prohibited_content\",\"message\":\"blocked\"}]}");

        assertThat(client.requestCompletion(REQUEST).isRefused()).isTrue();
    }

    @Test
    void recordsOneCallLogWithProviderModelAndKeyNickname() {
        client.requestCompletion(REQUEST);

        ArgumentCaptor<AiCallAttempt> attempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        verify(fixture.aiCallLogService).recordSuccess(attempt.capture(), eq(new AiTokenUsage(812, 96)));
        assertThat(attempt.getValue().callType()).isEqualTo(AiCallType.SUMMARY);
        assertThat(attempt.getValue().providerCode()).isEqualTo("google");
        assertThat(attempt.getValue().model()).isEqualTo(MODEL);
        assertThat(attempt.getValue().keyLabel()).isEqualTo("구글 운영");
        assertThat(attempt.getValue().toString()).doesNotContain(FIRST_API_KEY);
    }

    @Test
    void movesToNextKeyOnAuthenticationErrorAndCoolsDownFailedKey() {
        fixture.givenCallableKeys(FIRST_KEY, SECOND_KEY);
        respondByKey(new StubResponse(401, "{\"error\":{\"code\":\"authentication\",\"message\":\"API key not valid\"}}"),
                new StubResponse(200, toInteraction("completed", "{}", 10, 5, 0)));

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion.responseJson()).isEqualTo("{}");
        ArgumentCaptor<AiCallAttempt> failedAttempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        ArgumentCaptor<AiCallAttempt> succeededAttempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        InOrder callLogOrder = inOrder(fixture.aiCallLogService);
        callLogOrder.verify(fixture.aiCallLogService).recordFailure(failedAttempt.capture(), eq(AiFailureKind.UNAUTHORIZED));
        callLogOrder.verify(fixture.aiCallLogService).recordSuccess(succeededAttempt.capture(), any());
        assertThat(failedAttempt.getValue().keyLabel()).isEqualTo("구글 운영");
        assertThat(succeededAttempt.getValue().keyLabel()).isEqualTo("구글 예비");
        assertThat(fixture.tracker.isCoolingDown("key-1")).isTrue();

        // 다음 호출: 쿨다운 중인 첫째 키는 뒤로, 둘째 키만 부름
        client.requestCompletion(REQUEST);
        assertThat(server.receivedRequests()).extracting(request -> request.headers().getFirst(API_KEY_HEADER))
                .containsExactly(FIRST_API_KEY, SECOND_API_KEY, SECOND_API_KEY);
    }

    @Test
    void treatsFailedPreconditionAsPaymentTroubleAndTriesNextKey() {
        fixture.givenCallableKeys(FIRST_KEY, SECOND_KEY);
        respondByKey(new StubResponse(400, "{\"error\":{\"code\":\"failed_precondition\",\"message\":\"Billing is disabled\"}}"),
                new StubResponse(200, toInteraction("completed", "{}", 10, 5, 0)));

        assertThat(client.requestCompletion(REQUEST).responseJson()).isEqualTo("{}");
        verify(fixture.aiCallLogService).recordFailure(any(), eq(AiFailureKind.PAYMENT_REQUIRED));
        assertThat(fixture.tracker.isCoolingDown("key-1")).isTrue();
    }

    @Test
    void doesNotTryNextKeyForInvalidRequestOrMissingModel() {
        fixture.givenCallableKeys(FIRST_KEY, SECOND_KEY);
        server.respond(400, "{\"error\":{\"code\":\"invalid_request\",\"message\":\"schema too complex\"}}");
        assertThatThrownBy(() -> client.requestCompletion(REQUEST)).hasMessageContaining("BAD_REQUEST");

        server.respond(404, "{\"error\":{\"code\":\"model_not_found\",\"message\":\"no such model\"}}");
        assertThatThrownBy(() -> client.requestCompletion(REQUEST)).hasMessageContaining("NOT_FOUND");

        assertThat(server.receivedRequests()).hasSize(2);
        assertThat(fixture.tracker.isCoolingDown("key-1")).isFalse();
    }

    @Test
    void isUnavailableAndDoesNotCallWithoutKeys() {
        fixture.givenCallableKeys();

        assertThat(client.getProviderCode()).isEqualTo("google");
        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("provider not configured");
        assertThat(server.receivedRequests()).isEmpty();
    }

    @Test
    void readsModelAndThinkingLevelFromConfigStoreOnEveryCall() throws Exception {
        client.requestCompletion(REQUEST);
        when(fixture.systemConfigService.getString(ConfigKey.GOOGLE_MODEL)).thenReturn("changed-model");
        when(fixture.systemConfigService.getString(ConfigKey.GOOGLE_THINKING_LEVEL)).thenReturn("low");

        client.requestCompletion(REQUEST);

        JsonNode requestBody = OBJECT_MAPPER.readTree(server.lastRequest().body());
        assertThat(requestBody.get("model").asText()).isEqualTo("changed-model");
        assertThat(requestBody.get("generation_config").get("thinking_level").asText()).isEqualTo("low");
    }

    @Test
    void worksInsideTitleAnalysisFlow() {
        server.respond(200, toInteraction("completed",
                "{\"results\":[{\"number\":1,\"topic\":\"remoteAccess\",\"translatedTitle\":\"Example VPN 심각한 결함\"}]}", 50, 30, 0));
        ArticleTitleAnalyzer analyzer = new StructuredOutputArticleTitleAnalyzer(createClient(server.baseUrl() + "/"));

        assertThat(analyzer.analyzeBatch(TITLE_REQUESTS)).hasSize(1).allMatch(ArticleTitleAnalysisOutcome::isSuccessful);
    }

    private GoogleStructuredCompletionClient createClient(String baseUrl) {
        return new GoogleStructuredCompletionClient(fixture.createCallRunner(), fixture.createModelService(),
                fixture.systemConfigService, new ProviderHttpClient(), baseUrl);
    }

    // 첫째 키·둘째 키에 서로 다른 응답
    private void respondByKey(StubResponse firstKeyResponse, StubResponse secondKeyResponse) {
        server.respondBy(request -> FIRST_API_KEY.equals(request.headers().getFirst(API_KEY_HEADER))
                ? firstKeyResponse
                : secondKeyResponse);
    }

    // Interactions API 응답 모양: status, steps[model_output > content[text]], usage(total_*_tokens)
    private static String toInteraction(String status, String responseText, int inputTokens, int outputTokens,
                                        int thoughtTokens) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("total_input_tokens", inputTokens);
        usage.put("total_output_tokens", outputTokens);
        if (thoughtTokens > 0) {
            usage.put("total_thought_tokens", thoughtTokens);
        }
        usage.put("total_tokens", inputTokens + outputTokens + thoughtTokens);

        Map<String, Object> interaction = new LinkedHashMap<>();
        interaction.put("id", "int_test");
        interaction.put("status", status);
        interaction.put("model", MODEL);
        interaction.put("steps", List.of(Map.of("type", "model_output",
                "content", List.of(Map.of("type", "text", "text", responseText)))));
        interaction.put("usage", usage);
        return toJson(interaction);
    }

    private static String toJson(Map<String, Object> body) {
        try {
            return OBJECT_MAPPER.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
