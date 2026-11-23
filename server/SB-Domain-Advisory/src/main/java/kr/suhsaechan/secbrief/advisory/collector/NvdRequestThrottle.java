package kr.suhsaechan.secbrief.advisory.collector;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * NVD 요청 간격 유지: 직전 요청 후 6초가 안 지났으면 남은 시간만큼 대기
 * NVD 키 없는 한도 30초당 5회 > 최소 6초 간격
 */
public class NvdRequestThrottle {

    public static final Duration MINIMUM_REQUEST_INTERVAL = Duration.ofSeconds(6);

    private final Clock clock;
    private final Sleeper sleeper;
    // 직전 요청 시각 (요청한 적 없으면 null)
    private Instant lastRequestedAt;

    public NvdRequestThrottle(Clock clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * 요청 차례 대기 > 요청 시각 기록 (대기 중 인터럽트는 EXTERNAL_API_ERROR)
     */
    public synchronized void awaitTurn() {
        Instant now = clock.instant();
        if (lastRequestedAt != null) {
            Duration remaining = Duration.between(now, lastRequestedAt.plus(MINIMUM_REQUEST_INTERVAL));
            if (remaining.isPositive()) {
                sleepFor(remaining);
                now = now.plus(remaining);
            }
        }
        lastRequestedAt = now;
    }

    private void sleepFor(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "interrupted while waiting nvd rate limit");
        }
    }

    /**
     * 대기 방법 (운영: Thread.sleep, 테스트: 기록만)
     */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
