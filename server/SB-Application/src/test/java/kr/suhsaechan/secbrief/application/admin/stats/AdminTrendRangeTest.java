package kr.suhsaechan.secbrief.application.admin.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 추이 조회 범위: 기본 기간(일별 30일, 월별 12달), 틀린 값 보정, 366일 상한, 칸 목록과 x축 이름
 */
class AdminTrendRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    @Test
    void defaultsToLastThirtyDaysByDay() {
        AdminTrendRange range = AdminTrendRange.of(null, null, null, TODAY);

        assertThat(range.unit()).isEqualTo(AdminTrendUnit.DAY);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 28));
        assertThat(range.to()).isEqualTo(TODAY);
        assertThat(range.unit().bucketStartsBetween(range.from(), range.to())).hasSize(30);
    }

    @Test
    void defaultsToLastTwelveMonthsByMonth() {
        AdminTrendRange range = AdminTrendRange.of("month", null, null, TODAY);

        assertThat(range.unit()).isEqualTo(AdminTrendUnit.MONTH);
        assertThat(range.from()).isEqualTo(LocalDate.of(2025, 12, 1));
        List<LocalDate> bucketStarts = range.unit().bucketStartsBetween(range.from(), range.to());
        assertThat(bucketStarts).hasSize(12);
        assertThat(bucketStarts.get(0)).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(bucketStarts.get(11)).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void fallsBackToDefaultsOnInvalidValues() {
        AdminTrendRange range = AdminTrendRange.of("week", "not-a-date", "2026-13-40", TODAY);

        assertThat(range).isEqualTo(new AdminTrendRange(AdminTrendUnit.DAY, LocalDate.of(2026, 10, 28), TODAY));
    }

    @Test
    void clampsFutureEndToToday() {
        AdminTrendRange range = AdminTrendRange.of("day", "2026-11-20", "2027-01-01", TODAY);

        assertThat(range.to()).isEqualTo(TODAY);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void swapsReversedDates() {
        AdminTrendRange range = AdminTrendRange.of("day", "2026-11-20", "2026-11-10", TODAY);

        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void limitsRangeToMaximumDays() {
        AdminTrendRange range = AdminTrendRange.of("day", "2020-01-01", "2026-11-26", TODAY);

        assertThat(range.from()).isEqualTo(TODAY.minusDays(AdminTrendRange.MAXIMUM_DAY_COUNT - 1L));
        assertThat(range.unit().bucketStartsBetween(range.from(), range.to())).hasSize(AdminTrendRange.MAXIMUM_DAY_COUNT);
    }

    @Test
    void limitsMonthRangeToMaximumDays() {
        AdminTrendRange range = AdminTrendRange.of("month", "2015-01-01", null, TODAY);

        // 366일 전(2025-11-26)이 속한 달부터 13칸
        assertThat(range.unit().bucketStartsBetween(range.from(), range.to())).hasSize(13);
    }

    @Test
    void labelsBucketsByUnit() {
        assertThat(AdminTrendUnit.DAY.labelOf(LocalDate.of(2026, 11, 6))).isEqualTo("11/6");
        assertThat(AdminTrendUnit.MONTH.labelOf(LocalDate.of(2026, 3, 1))).isEqualTo("2026-03");
        assertThat(AdminTrendUnit.MONTH.bucketStartOf(LocalDate.of(2026, 3, 19))).isEqualTo(LocalDate.of(2026, 3, 1));
    }
}
