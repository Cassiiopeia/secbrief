package kr.suhsaechan.secbrief.advisory.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * NVD 요청 간격: 첫 요청은 바로, 6초 안의 다음 요청은 남은 시간만큼 대기
 */
class NvdRequestThrottleTest {

    private static final Instant T0 = Instant.parse("2026-11-23T00:00:00Z");

    private final MovableClock clock = new MovableClock(T0);
    private final List<Duration> sleptDurations = new ArrayList<>();
    private final NvdRequestThrottle throttle = new NvdRequestThrottle(clock, sleptDurations::add);

    @Test
    void firstRequestDoesNotWait() {
        throttle.awaitTurn();

        assertThat(sleptDurations).isEmpty();
    }

    @Test
    void secondRequestWaitsRemainingInterval() {
        throttle.awaitTurn();
        clock.moveTo(T0.plusSeconds(2));

        throttle.awaitTurn();

        assertThat(sleptDurations).containsExactly(Duration.ofSeconds(4));
    }

    @Test
    void requestAfterIntervalDoesNotWait() {
        throttle.awaitTurn();
        clock.moveTo(T0.plusSeconds(7));

        throttle.awaitTurn();

        assertThat(sleptDurations).isEmpty();
    }

    // 테스트 중 시각을 옮길 수 있는 시계
    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        private void moveTo(Instant movedNow) {
            this.now = movedNow;
        }

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
            return now;
        }
    }
}
