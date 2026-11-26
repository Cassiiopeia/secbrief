package kr.suhsaechan.secbrief.advisory.ai.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.advisory.support.MutableClock;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

/**
 * 키로 부르는 제공자 공통 호출 (실제 키 목록 관리 + 키 순회 + 고정 시계, 가짜 호출 함수): 제공자 3개가 같은 규칙으로 동작
 * 제공자별 키 목록 분리, 우선순위·다음 키 넘김·쿨다운과 복귀, 키마다 호출 기록 1행(제공자·모델·별명), 쓸 키 없음
 */
class AiApiKeyCallRunnerTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");
    private static final String ACTOR = "admin";
    private static final String MODEL = "test-model";
    private static final AiTokenUsage TOKEN_USAGE = new AiTokenUsage(100, 20);

    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final AiCallLogService aiCallLogService = mock(AiCallLogService.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final ApiKeyHealthTracker tracker = new ApiKeyHealthTracker(clock);
    // 가짜 설정 저장소가 들고 있는 제공자별 키 목록 평문
    private final Map<ConfigKey, String> storedSecretByConfigKey = new HashMap<>();
    // 키 값별 실패 종류 (없으면 성공), 부른 키 값 순서
    private final Map<String, AiFailureKind> failureKindByApiKey = new HashMap<>();
    private final List<String> calledApiKeys = new ArrayList<>();
    private AiApiKeyServiceRegistry registry;
    private AiApiKeyCallRunner runner;

    @BeforeEach
    void givenInMemoryConfigStore() {
        when(systemConfigService.getSecret(any())).thenAnswer(
                invocation -> storedSecretByConfigKey.getOrDefault(invocation.<ConfigKey>getArgument(0), ""));
        when(systemConfigService.update(any(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            storedSecretByConfigKey.put(invocation.getArgument(0), invocation.getArgument(1));
            return true;
        });
        registry = new AiApiKeyServiceRegistry(systemConfigService, tracker, new MockEnvironment());
        runner = new AiApiKeyCallRunner(registry, new ApiKeyFallbackExecutor(tracker), aiCallLogService);
    }

    @ParameterizedTest
    @EnumSource(KeyedAiProvider.class)
    void fallsBackToNextKeyAndRecordsEachAttemptForEveryProvider(KeyedAiProvider provider) {
        String firstApiKey = toApiKey(provider, "first");
        String secondApiKey = toApiKey(provider, "second");
        registry.find(provider).add("운영", firstApiKey, ACTOR);
        registry.find(provider).add("예비", secondApiKey, ACTOR);
        failureKindByApiKey.put(firstApiKey, AiFailureKind.RATE_LIMITED);

        assertThat(call(provider)).isEqualTo("answer from " + secondApiKey);

        assertThat(calledApiKeys).containsExactly(firstApiKey, secondApiKey);
        ArgumentCaptor<AiCallAttempt> failedAttempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        ArgumentCaptor<AiCallAttempt> succeededAttempt = ArgumentCaptor.forClass(AiCallAttempt.class);
        verify(aiCallLogService).recordFailure(failedAttempt.capture(), eq(AiFailureKind.RATE_LIMITED));
        verify(aiCallLogService).recordSuccess(succeededAttempt.capture(), eq(TOKEN_USAGE));
        assertThat(failedAttempt.getValue()).extracting(AiCallAttempt::callType, AiCallAttempt::providerCode,
                        AiCallAttempt::model, AiCallAttempt::keyLabel)
                .containsExactly(AiCallType.SUMMARY, provider.getProviderCode(), MODEL, "운영");
        assertThat(succeededAttempt.getValue().keyLabel()).isEqualTo("예비");
        assertThat(succeededAttempt.getValue().toString()).doesNotContain(secondApiKey);
    }

    @Test
    void putsFailedKeyBehindForFiveMinutesThenRestoresPriority() {
        String firstApiKey = toApiKey(KeyedAiProvider.OPENAI, "first");
        String secondApiKey = toApiKey(KeyedAiProvider.OPENAI, "second");
        registry.find(KeyedAiProvider.OPENAI).add("운영", firstApiKey, ACTOR);
        registry.find(KeyedAiProvider.OPENAI).add("예비", secondApiKey, ACTOR);
        failureKindByApiKey.put(firstApiKey, AiFailureKind.UNAUTHORIZED);
        call(KeyedAiProvider.OPENAI);
        failureKindByApiKey.clear();
        calledApiKeys.clear();

        // 쿨다운 중: 둘째 키부터
        clock.advance(ApiKeyHealthTracker.FAILURE_COOLDOWN.minusSeconds(1));
        call(KeyedAiProvider.OPENAI);
        assertThat(calledApiKeys).containsExactly(secondApiKey);

        // 5분 뒤: 다시 첫째 키부터
        clock.advance(Duration.ofSeconds(1));
        calledApiKeys.clear();
        call(KeyedAiProvider.OPENAI);
        assertThat(calledApiKeys).containsExactly(firstApiKey);
    }

    @Test
    void usesOnlyKeysOfRequestedProvider() {
        String openAiApiKey = toApiKey(KeyedAiProvider.OPENAI, "only");
        registry.find(KeyedAiProvider.OPENAI).add("운영", openAiApiKey, ACTOR);

        assertThat(runner.hasCallableKey(KeyedAiProvider.OPENAI)).isTrue();
        assertThat(runner.hasCallableKey(KeyedAiProvider.GOOGLE)).isFalse();
        assertThat(runner.hasCallableKey(KeyedAiProvider.ANTHROPIC)).isFalse();
        assertThat(storedSecretByConfigKey).containsOnlyKeys(ConfigKey.OPENAI_API_KEYS);
        assertThatThrownBy(() -> call(KeyedAiProvider.GOOGLE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("provider not configured")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(calledApiKeys).isEmpty();
        verify(aiCallLogService, never()).recordFailure(any(), any());
    }

    @Test
    void skipsDisabledKeysAndStopsAtFailureAnotherKeyCannotFix() {
        String firstApiKey = toApiKey(KeyedAiProvider.GOOGLE, "first");
        String secondApiKey = toApiKey(KeyedAiProvider.GOOGLE, "second");
        String thirdApiKey = toApiKey(KeyedAiProvider.GOOGLE, "third");
        AiApiKeyService googleKeyService = registry.find(KeyedAiProvider.GOOGLE);
        googleKeyService.setEnabled(googleKeyService.add("꺼 둔 키", firstApiKey, ACTOR).keyId(), false, ACTOR);
        googleKeyService.add("운영", secondApiKey, ACTOR);
        googleKeyService.add("예비", thirdApiKey, ACTOR);
        failureKindByApiKey.put(secondApiKey, AiFailureKind.BAD_REQUEST);

        assertThatThrownBy(() -> call(KeyedAiProvider.GOOGLE))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("BAD_REQUEST")
                .hasMessageNotContaining(secondApiKey);
        // 꺼 둔 키는 부르지 않고, 요청 문제는 다음 키로 넘기지 않음
        assertThat(calledApiKeys).containsExactly(secondApiKey);
        verify(aiCallLogService, times(1)).recordFailure(any(), eq(AiFailureKind.BAD_REQUEST));
    }

    private String call(KeyedAiProvider provider) {
        return runner.call(new AiApiKeyCall<>(provider, MODEL, AiCallType.SUMMARY, this::callWithKey,
                response -> TOKEN_USAGE, exception -> ((FakeCallFailure) exception).failureKind));
    }

    private String callWithKey(AiApiKey key) {
        calledApiKeys.add(key.apiKey());
        AiFailureKind failureKind = failureKindByApiKey.get(key.apiKey());
        if (failureKind != null) {
            throw new FakeCallFailure(failureKind);
        }
        return "answer from " + key.apiKey();
    }

    private static String toApiKey(KeyedAiProvider provider, String keyName) {
        return "test-" + provider.getProviderCode() + "-" + keyName + "-000000000000";
    }

    private static final class FakeCallFailure extends RuntimeException {

        private final AiFailureKind failureKind;

        private FakeCallFailure(AiFailureKind failureKind) {
            this.failureKind = failureKind;
        }
    }
}
