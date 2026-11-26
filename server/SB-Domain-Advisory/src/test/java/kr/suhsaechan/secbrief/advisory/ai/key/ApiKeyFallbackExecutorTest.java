package kr.suhsaechan.secbrief.advisory.ai.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.support.MutableClock;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 키 순회 (가짜 호출 함수 + 고정 시계): 우선순위 순서, 다음 키로 넘기는 실패·넘기지 않는 실패, 쿨다운과 복귀, 전부 실패
 */
class ApiKeyFallbackExecutorTest {

    private static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");
    private static final AnthropicApiKey FIRST_KEY = new AnthropicApiKey("key-1", "첫째", "first-secret-value-000000", true);
    private static final AnthropicApiKey SECOND_KEY = new AnthropicApiKey("key-2", "둘째", "second-secret-value-00000", true);
    private static final AnthropicApiKey THIRD_KEY = new AnthropicApiKey("key-3", "셋째", "third-secret-value-000000", true);
    private static final List<AnthropicApiKey> KEYS = List.of(FIRST_KEY, SECOND_KEY, THIRD_KEY);

    private final MutableClock clock = new MutableClock(NOW);
    private final ApiKeyHealthTracker tracker = new ApiKeyHealthTracker(clock);
    private final ApiKeyFallbackExecutor executor = new ApiKeyFallbackExecutor(tracker);
    private final FakeKeyCall fakeKeyCall = new FakeKeyCall();

    @Test
    void usesHighestPriorityKeyWhenItSucceeds() {
        assertThat(execute()).isEqualTo("answer from key-1");
        assertThat(fakeKeyCall.calledKeyIds).containsExactly("key-1");
        assertThat(tracker.findHealth("key-1").orElseThrow().lastSuccessAt()).isEqualTo(NOW);
    }

    @ParameterizedTest
    @EnumSource(value = AiFailureKind.class, names = {"UNAUTHORIZED", "PAYMENT_REQUIRED", "PERMISSION_DENIED",
            "RATE_LIMITED", "OVERLOADED", "SERVER_ERROR", "CONNECTION_FAILED", "CREDIT_EXHAUSTED"})
    void triesNextKeyOnKeyOrProviderTroubles(AiFailureKind failureKind) {
        fakeKeyCall.fail("key-1", failureKind);

        assertThat(execute()).isEqualTo("answer from key-2");
        assertThat(fakeKeyCall.calledKeyIds).containsExactly("key-1", "key-2");
        assertThat(tracker.isCoolingDown("key-1")).isTrue();
        assertThat(tracker.findHealth("key-1").orElseThrow().lastFailureKind()).isEqualTo(failureKind);
    }

    @ParameterizedTest
    @EnumSource(value = AiFailureKind.class, names = {"BAD_REQUEST", "NOT_FOUND", "CLIENT_ERROR",
            "INVALID_CONFIGURATION", "UNKNOWN"})
    void failsImmediatelyWithoutNextKeyWhenAnotherKeyWouldNotHelp(AiFailureKind failureKind) {
        fakeKeyCall.fail("key-1", failureKind);

        assertThatThrownBy(this::execute)
                .isInstanceOf(CustomException.class)
                .hasMessageContaining(failureKind.name())
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(fakeKeyCall.calledKeyIds).containsExactly("key-1");
        // 키 탓이 아니라 쿨다운에 넣지 않음
        assertThat(tracker.isCoolingDown("key-1")).isFalse();
    }

    @Test
    void putsCoolingKeyBehindOthersAndRestoresPriorityAfterCooldown() {
        fakeKeyCall.fail("key-1", AiFailureKind.RATE_LIMITED);
        execute();
        fakeKeyCall.recover("key-1");
        fakeKeyCall.calledKeyIds.clear();

        // 쿨다운 중: 둘째 키부터 (첫째는 맨 뒤로)
        clock.advance(ApiKeyHealthTracker.FAILURE_COOLDOWN.minusSeconds(1));
        assertThat(execute()).isEqualTo("answer from key-2");
        assertThat(fakeKeyCall.calledKeyIds).containsExactly("key-2");

        // 5분 뒤: 다시 첫째 키부터
        clock.advance(Duration.ofSeconds(1));
        fakeKeyCall.calledKeyIds.clear();
        assertThat(execute()).isEqualTo("answer from key-1");
        assertThat(fakeKeyCall.calledKeyIds).containsExactly("key-1");
    }

    @Test
    void stillTriesCoolingKeysWhenEveryKeyIsCoolingDown() {
        KEYS.forEach(key -> fakeKeyCall.fail(key.id(), AiFailureKind.OVERLOADED));
        assertThatThrownBy(this::execute).isInstanceOf(CustomException.class);
        fakeKeyCall.calledKeyIds.clear();
        fakeKeyCall.recover("key-3");

        // 전부 쿨다운이어도 우선순위 순으로 끝까지 시도 (키 상태 때문에 AI 처리가 멈추지 않게)
        assertThat(execute()).isEqualTo("answer from key-3");
        assertThat(fakeKeyCall.calledKeyIds).containsExactly("key-1", "key-2", "key-3");
        assertThat(tracker.isCoolingDown("key-3")).isFalse();
    }

    @Test
    void throwsAiApiErrorWithLastFailureKindWhenAllKeysFail() {
        fakeKeyCall.fail("key-1", AiFailureKind.UNAUTHORIZED);
        fakeKeyCall.fail("key-2", AiFailureKind.CREDIT_EXHAUSTED);
        fakeKeyCall.fail("key-3", AiFailureKind.RATE_LIMITED);

        assertThatThrownBy(this::execute)
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("all keys failed")
                .hasMessageContaining("RATE_LIMITED")
                .hasMessageNotContaining("secret-value")
                .extracting("errorCode").isEqualTo(ErrorCode.AI_API_ERROR);
        assertThat(fakeKeyCall.calledKeyIds).containsExactly("key-1", "key-2", "key-3");
    }

    @Test
    void stopsAtNonKeyFailureEvenAfterPassingOverEarlierKeys() {
        fakeKeyCall.fail("key-1", AiFailureKind.UNAUTHORIZED);
        fakeKeyCall.fail("key-2", AiFailureKind.BAD_REQUEST);

        assertThatThrownBy(this::execute).hasMessageContaining("BAD_REQUEST");
        assertThat(fakeKeyCall.calledKeyIds).containsExactly("key-1", "key-2");
    }

    @Test
    void throwsAiApiErrorWithoutCallingWhenThereIsNoKey() {
        assertThatThrownBy(() -> executor.execute(List.of(), fakeKeyCall::call, FakeKeyCall::classify))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("no api key");
        assertThat(fakeKeyCall.calledKeyIds).isEmpty();
    }

    private String execute() {
        return executor.execute(KEYS, fakeKeyCall::call, FakeKeyCall::classify);
    }

    /** 가짜 호출: 키별로 정해 둔 실패 종류를 던지거나 응답을 돌려줌, 부른 키 순서를 기록 */
    private static final class FakeKeyCall {

        private final Map<String, AiFailureKind> failureKindByKeyId = new HashMap<>();
        private final List<String> calledKeyIds = new ArrayList<>();

        private void fail(String keyId, AiFailureKind failureKind) {
            failureKindByKeyId.put(keyId, failureKind);
        }

        private void recover(String keyId) {
            failureKindByKeyId.remove(keyId);
        }

        private String call(AnthropicApiKey key) {
            calledKeyIds.add(key.id());
            AiFailureKind failureKind = failureKindByKeyId.get(key.id());
            if (failureKind != null) {
                throw new FakeCallFailure(failureKind);
            }
            return "answer from " + key.id();
        }

        private static AiFailureKind classify(RuntimeException exception) {
            return ((FakeCallFailure) exception).failureKind;
        }
    }

    private static final class FakeCallFailure extends RuntimeException {

        private final AiFailureKind failureKind;

        private FakeCallFailure(AiFailureKind failureKind) {
            this.failureKind = failureKind;
        }
    }
}
