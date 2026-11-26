package kr.suhsaechan.secbrief.advisory.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;

/**
 * 테스트용 시계: 시작 시각에 멈춰 있고 advance 로만 흐름 (쿨다운·캐시 만료·날짜 넘김 확인용)
 */
public final class MutableClock extends Clock {

    private Instant currentInstant;

    public MutableClock(Instant startInstant) {
        this.currentInstant = startInstant;
    }

    public void advance(Duration duration) {
        currentInstant = currentInstant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return SeoulCalendar.SEOUL;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return currentInstant;
    }
}
