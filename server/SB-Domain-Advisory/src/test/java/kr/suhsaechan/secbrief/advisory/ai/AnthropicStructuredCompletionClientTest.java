package kr.suhsaechan.secbrief.advisory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKey;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKeyService;
import kr.suhsaechan.secbrief.advisory.ai.key.ApiKeyFallbackExecutor;
import kr.suhsaechan.secbrief.advisory.ai.key.ApiKeyHealthTracker;
import kr.suhsaechan.secbrief.advisory.support.MutableClock;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Anthropic 제공자 (가짜 SDK 클라이언트, 실제 호출 없음): 요청 모양, 키 우선순위·다음 키 넘김, 키별 호출 기록, 사용량, 클라이언트 캐시
 */
class AnthropicStructuredCompletionClientTest {

    private static final String MODEL = "claude-haiku-5-5";
    private static final String FIRST_API_KEY = "sk-test-first-000000000000-AAAA";
    private static final String SECOND_API_KEY = "sk-test-second-00000000000-BBBB";
    private static final AnthropicApiKey FIRST_KEY = new AnthropicApiKey("key-1", "운영", FIRST_API_KEY, true);
    private static final AnthropicApiKey SECOND_KEY = new AnthropicApiKey("key-2", "예비", SECOND_API_KEY, true);
    private static final StructuredCompletionRequest REQUEST = new StructuredCompletionRequest(
            AiCallType.TITLE_ANALYSIS, "instruction", "{\"titles\":[]}", Map.of("type", "object"), 256);
    private static final Headers NO_HEADERS = Headers.builder().build();

    private final AnthropicApiKeyService anthropicApiKeyService = mock(AnthropicApiKeyService.class);
    private final AiCallLogService aiCallLogService = mock(AiCallLogService.class);
    private final ApiKeyHealthTracker tracker = new ApiKeyHealthTracker(new MutableClock(Instant.parse("2026-11-26T00:00:00Z")));
    // 키 값별 가짜 SDK 클라이언트 (만든 순서 기록)
    private final Map<String, MessageService> messageServiceByApiKey = new HashMap<>();
    private final List<String> createdClientApiKeys = new ArrayList<>();
    private final AnthropicStructuredCompletionClient client = new AnthropicStructuredCompletionClient(
            anthropicApiKeyService, new AnthropicClientCache(this::createFakeClient),
            new ApiKeyFallbackExecutor(tracker), aiCallLogService, MODEL);

    @Test
    void isUnavailableAndDoesNotCallWithoutKeys() {
        when(anthropicApiKeyService.hasCallableKey()).thenReturn(false);
        when(anthropicApiKeyService.findCallableKeys()).thenReturn(List.of());

        assertThat(client.getProviderCode()).isEqualTo("anthropic");
        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("provider not configured");
        assertThat(createdClientApiKeys).isEmpty();
    }

    @Test
    void sendsStructuredOutputRequestAndReturnsJsonWithUsage() {
        givenCallableKeys(FIRST_KEY);
        givenResponse(FIRST_API_KEY, toMessage("{\"results\":[]}", StopReason.END_TURN, 812, 96));

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion).isEqualTo(StructuredCompletion.of("{\"results\":[]}", false, new AiTokenUsage(812, 96)));
        MessageCreateParams params = captureParams(FIRST_API_KEY);
        // 사용자 지시: Haiku 5.5 고정, 생각 끄고 낮은 노력, 구조화 출력
        assertThat(params.model().asString()).isEqualTo(MODEL);
        assertThat(params.maxTokens()).isEqualTo(256);
        assertThat(params.thinking().orElseThrow().isDisabled()).isTrue();
        assertThat(params.outputConfig().orElseThrow().effort()).contains(OutputConfig.Effort.LOW);
        assertThat(params.outputConfig().orElseThrow().format()).isPresent();
    }

    @Test
    void recordsOneCallLogWithKeyNicknameNotKeyValue() {
        givenCallableKeys(FIRST_KEY);
        givenResponse(FIRST_API_KEY, toMessage("{}", StopReason.END_TURN, 812, 96));

        client.requestCompletion(REQUEST);

        ArgumentCaptor<AiCallAttempt> attempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        verify(aiCallLogService).recordSuccess(attempt.capture(), eq(new AiTokenUsage(812, 96)));
        assertThat(attempt.getValue().callType()).isEqualTo(AiCallType.TITLE_ANALYSIS);
        assertThat(attempt.getValue().providerCode()).isEqualTo("anthropic");
        assertThat(attempt.getValue().model()).isEqualTo(MODEL);
        assertThat(attempt.getValue().keyLabel()).isEqualTo("운영");
        assertThat(attempt.getValue().toString()).doesNotContain(FIRST_API_KEY);
    }

    @Test
    void movesToNextKeyOnKeyTroubleAndRecordsBothAttempts() {
        givenCallableKeys(FIRST_KEY, SECOND_KEY);
        givenFailure(FIRST_API_KEY, UnauthorizedException.builder().headers(NO_HEADERS)
                .body(toErrorBody("invalid x-api-key")).build());
        givenResponse(SECOND_API_KEY, toMessage("{}", StopReason.END_TURN, 10, 5));

        StructuredCompletion completion = client.requestCompletion(REQUEST);

        assertThat(completion.responseJson()).isEqualTo("{}");
        ArgumentCaptor<AiCallAttempt> failedAttempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        ArgumentCaptor<AiCallAttempt> succeededAttempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        InOrder callLogOrder = inOrder(aiCallLogService);
        callLogOrder.verify(aiCallLogService).recordFailure(failedAttempt.capture(), eq(AiFailureKind.UNAUTHORIZED));
        callLogOrder.verify(aiCallLogService).recordSuccess(succeededAttempt.capture(), any());
        assertThat(failedAttempt.getValue().keyLabel()).isEqualTo("운영");
        assertThat(succeededAttempt.getValue().keyLabel()).isEqualTo("예비");
        assertThat(tracker.isCoolingDown("key-1")).isTrue();

        // 다음 호출: 쿨다운 중인 첫째 키는 뒤로, 둘째 키만 부름
        client.requestCompletion(REQUEST);
        verify(messageServiceByApiKey.get(FIRST_API_KEY), times(1)).create(any(MessageCreateParams.class));
        verify(messageServiceByApiKey.get(SECOND_API_KEY), times(2)).create(any(MessageCreateParams.class));
    }

    @Test
    void throwsAiApiErrorWhenAllKeysFail() {
        givenCallableKeys(FIRST_KEY, SECOND_KEY);
        givenFailure(FIRST_API_KEY, RateLimitException.builder().headers(NO_HEADERS).body(toErrorBody("rate limited")).build());
        givenFailure(SECOND_API_KEY, BadRequestException.builder().headers(NO_HEADERS)
                .body(toErrorBody("Your credit balance is too low to access the Anthropic API.")).build());

        assertThatThrownBy(() -> client.requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("all keys failed")
                .hasMessageNotContaining("sk-test")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        verify(aiCallLogService).recordFailure(any(), eq(AiFailureKind.RATE_LIMITED));
        verify(aiCallLogService).recordFailure(any(), eq(AiFailureKind.CREDIT_EXHAUSTED));
        verify(aiCallLogService, never()).recordSuccess(any(), any());
    }

    @Test
    void doesNotTryNextKeyForRequestProblem() {
        givenCallableKeys(FIRST_KEY, SECOND_KEY);
        givenFailure(FIRST_API_KEY, BadRequestException.builder().headers(NO_HEADERS)
                .body(toErrorBody("max_tokens: must be greater than 0")).build());

        assertThatThrownBy(() -> client.requestCompletion(REQUEST))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("BAD_REQUEST");
        verify(aiCallLogService).recordFailure(any(), eq(AiFailureKind.BAD_REQUEST));
        assertThat(createdClientApiKeys).containsExactly(FIRST_API_KEY);
        assertThat(tracker.isCoolingDown("key-1")).isFalse();
    }

    @Test
    void returnsRefusedAndTruncatedCompletions() {
        givenCallableKeys(FIRST_KEY);
        givenResponse(FIRST_API_KEY, toMessage("", StopReason.REFUSAL, 30, 0));
        assertThat(client.requestCompletion(REQUEST).isRefused()).isTrue();

        givenResponse(FIRST_API_KEY, toMessage("{\"results\":[", StopReason.MAX_TOKENS, 30, 256));
        StructuredCompletion truncatedCompletion = client.requestCompletion(REQUEST);
        assertThat(truncatedCompletion.isTruncated()).isTrue();
        assertThat(truncatedCompletion.tokenUsage()).isEqualTo(new AiTokenUsage(30, 256));
    }

    @Test
    void reusesClientPerKeyAndRebuildsWhenKeyValueChanges() {
        givenCallableKeys(FIRST_KEY);
        givenResponse(FIRST_API_KEY, toMessage("{}", StopReason.END_TURN, 1, 1));
        client.requestCompletion(REQUEST);
        client.requestCompletion(REQUEST);
        assertThat(createdClientApiKeys).containsExactly(FIRST_API_KEY);

        // 같은 id 의 키 값 교체: 새 값으로 클라이언트를 다시 만듦
        givenCallableKeys(FIRST_KEY.withApiKey(SECOND_API_KEY));
        givenResponse(SECOND_API_KEY, toMessage("{}", StopReason.END_TURN, 1, 1));
        client.requestCompletion(REQUEST);
        assertThat(createdClientApiKeys).containsExactly(FIRST_API_KEY, SECOND_API_KEY);
    }

    private void givenCallableKeys(AnthropicApiKey... keys) {
        when(anthropicApiKeyService.findCallableKeys()).thenReturn(List.of(keys));
        when(anthropicApiKeyService.hasCallableKey()).thenReturn(keys.length > 0);
    }

    private void givenResponse(String apiKey, Message message) {
        when(findMessageService(apiKey).create(any(MessageCreateParams.class))).thenReturn(message);
    }

    private void givenFailure(String apiKey, RuntimeException failure) {
        when(findMessageService(apiKey).create(any(MessageCreateParams.class))).thenThrow(failure);
    }

    private MessageService findMessageService(String apiKey) {
        return messageServiceByApiKey.computeIfAbsent(apiKey, unusedApiKey -> mock(MessageService.class));
    }

    private AnthropicClient createFakeClient(String apiKey) {
        createdClientApiKeys.add(apiKey);
        AnthropicClient anthropicClient = mock(AnthropicClient.class);
        when(anthropicClient.messages()).thenReturn(findMessageService(apiKey));
        return anthropicClient;
    }

    private MessageCreateParams captureParams(String apiKey) {
        ArgumentCaptor<MessageCreateParams> params = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(findMessageService(apiKey)).create(params.capture());
        return params.getValue();
    }

    // SDK 응답 객체는 만들 필드가 많아 필요한 접근자만 흉내 냄
    private static Message toMessage(String responseText, StopReason stopReason, long inputTokens, long outputTokens) {
        TextBlock textBlock = mock(TextBlock.class);
        when(textBlock.text()).thenReturn(responseText);
        ContentBlock contentBlock = mock(ContentBlock.class);
        when(contentBlock.text()).thenReturn(Optional.of(textBlock));
        Usage usage = mock(Usage.class);
        when(usage.inputTokens()).thenReturn(inputTokens);
        when(usage.outputTokens()).thenReturn(outputTokens);
        Message message = mock(Message.class);
        when(message.content()).thenReturn(List.of(contentBlock));
        when(message.stopReason()).thenReturn(Optional.of(stopReason));
        when(message.usage()).thenReturn(usage);
        return message;
    }

    private static JsonValue toErrorBody(String message) {
        return JsonValue.from(Map.of("type", "error",
                "error", Map.of("type", "invalid_request_error", "message", message)));
    }
}
