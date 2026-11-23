package kr.suhsaechan.secbrief.advisory.policy;

import static kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar.startOfToday;
import static kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar.startOfWeek;
import static kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar.toSeoulDate;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 서울 달력: 오늘·이번 주(월요일 시작) 시작 시각과 서울 날짜
 */
class SeoulCalendarTest {

    @Test
    void startOfWeekIsMondayMidnightSeoul() {
        // 2026-11-25(수) 12:00 KST > 2026-11-23(월) 00:00 KST = 2026-11-22T15:00Z
        assertThat(startOfWeek(Instant.parse("2026-11-25T03:00:00Z"))).isEqualTo(Instant.parse("2026-11-22T15:00:00Z"));
    }

    @Test
    void startOfWeekOnMondayIsSameDayMidnight() {
        // 2026-11-23(월) 09:00 KST > 같은 날 00:00 KST
        assertThat(startOfWeek(Instant.parse("2026-11-23T00:00:00Z"))).isEqualTo(Instant.parse("2026-11-22T15:00:00Z"));
    }

    @Test
    void startOfWeekOnSundayGoesBackToPreviousMonday() {
        // 2026-11-29(일) 23:30 KST > 2026-11-23(월) 00:00 KST
        assertThat(startOfWeek(Instant.parse("2026-11-29T14:30:00Z"))).isEqualTo(Instant.parse("2026-11-22T15:00:00Z"));
    }

    @Test
    void startOfTodayUsesSeoulDate() {
        // 2026-11-23T16:30Z = 11-24 01:30 KST > 11-24 00:00 KST
        assertThat(startOfToday(Instant.parse("2026-11-23T16:30:00Z"))).isEqualTo(Instant.parse("2026-11-23T15:00:00Z"));
    }

    @Test
    void toSeoulDateRollsOverAtMidnightSeoul() {
        assertThat(toSeoulDate(Instant.parse("2026-11-23T14:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(toSeoulDate(Instant.parse("2026-11-23T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 24));
    }
}
