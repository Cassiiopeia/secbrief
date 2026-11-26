package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginAttemptLimiter;
import org.junit.jupiter.api.Test;

/**
 * 로그인 실패 제한: 5회째 잠금, 15분 뒤 풀림, 성공 시 초기화, 구간이 지난 실패는 세지 않음, 대소문자 변형 합산
 */
class AdminLoginAttemptLimiterTest {

    private static final String USERNAME = "operator";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-26T00:00:00Z"));
    private final AdminLoginAttemptLimiter limiter = new AdminLoginAttemptLimiter(new AdjustableClock());

    @Test
    void locksOnFifthFailure() {
        for (int attempt = 1; attempt < AdminLoginAttemptLimiter.MAXIMUM_FAILURE_COUNT; attempt++) {
            assertThat(limiter.recordFailure(USERNAME)).isFalse();
        }
        assertThat(limiter.isLocked(USERNAME)).isFalse();

        assertThat(limiter.recordFailure(USERNAME)).isTrue();
        assertThat(limiter.isLocked(USERNAME)).isTrue();
    }

    @Test
    void unlocksAfterLockDuration() {
        failTimes(USERNAME, AdminLoginAttemptLimiter.MAXIMUM_FAILURE_COUNT);

        advance(AdminLoginAttemptLimiter.LOCK_DURATION.minusSeconds(1));
        assertThat(limiter.isLocked(USERNAME)).isTrue();
        advance(Duration.ofSeconds(1));
        assertThat(limiter.isLocked(USERNAME)).isFalse();
        // 풀린 뒤 첫 실패는 다시 1회부터
        assertThat(limiter.recordFailure(USERNAME)).isFalse();
    }

    @Test
    void clearsFailuresOnSuccess() {
        failTimes(USERNAME, AdminLoginAttemptLimiter.MAXIMUM_FAILURE_COUNT - 1);

        limiter.clear(USERNAME);

        assertThat(limiter.recordFailure(USERNAME)).isFalse();
        assertThat(limiter.isLocked(USERNAME)).isFalse();
    }

    @Test
    void forgetsFailuresOlderThanWindow() {
        failTimes(USERNAME, AdminLoginAttemptLimiter.MAXIMUM_FAILURE_COUNT - 1);
        advance(AdminLoginAttemptLimiter.LOCK_DURATION);

        assertThat(limiter.recordFailure(USERNAME)).isFalse();
        assertThat(limiter.isLocked(USERNAME)).isFalse();
    }

    @Test
    void countsCaseAndSpaceVariantsAsSameUsername() {
        failTimes("Operator", 2);
        failTimes(" OPERATOR ", 2);

        assertThat(limiter.recordFailure(USERNAME)).isTrue();
        assertThat(limiter.isLocked("oPeRaToR")).isTrue();
    }

    @Test
    void countsUsernamesSeparately() {
        failTimes(USERNAME, AdminLoginAttemptLimiter.MAXIMUM_FAILURE_COUNT);

        assertThat(limiter.isLocked("another")).isFalse();
        assertThat(limiter.isLocked(null)).isFalse();
    }

    private void failTimes(String username, int failureCount) {
        for (int attempt = 0; attempt < failureCount; attempt++) {
            limiter.recordFailure(username);
        }
    }

    private void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    // 테스트가 시각을 옮기는 시계
    private final class AdjustableClock extends Clock {

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
