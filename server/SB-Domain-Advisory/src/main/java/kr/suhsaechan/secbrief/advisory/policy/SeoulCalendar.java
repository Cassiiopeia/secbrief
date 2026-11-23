package kr.suhsaechan.secbrief.advisory.policy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * 서울 달력: 오늘·이번 주(월요일 시작) 시작 시각, 서울 날짜
 * 오늘·이번 주 탭 기준이 서버·앱에서 같도록 한 곳에서 계산
 */
public final class SeoulCalendar {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private SeoulCalendar() {
    }

    /**
     * 서울 기준 오늘 00:00 의 시각
     */
    public static Instant startOfToday(Instant now) {
        return toSeoulDate(now).atStartOfDay(SEOUL).toInstant();
    }

    /**
     * 서울 기준 이번 주 월요일 00:00 의 시각 (월요일이면 오늘)
     */
    public static Instant startOfWeek(Instant now) {
        return toSeoulDate(now)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(SEOUL)
                .toInstant();
    }

    /**
     * 시각 > 서울 날짜
     */
    public static LocalDate toSeoulDate(Instant now) {
        return now.atZone(SEOUL).toLocalDate();
    }
}
