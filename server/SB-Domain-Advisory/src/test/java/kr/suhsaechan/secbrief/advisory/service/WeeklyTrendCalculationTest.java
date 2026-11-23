package kr.suhsaechan.secbrief.advisory.service;

import static kr.suhsaechan.secbrief.advisory.service.WeeklyTrendQueryService.toChangePercent;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 증감률: 반올림, 지난주 0 이면 null
 */
class WeeklyTrendCalculationTest {

    @Test
    void changePercentRoundsAndHandlesZeroPrevious() {
        assertThat(toChangePercent(12, 10)).isEqualTo(20);
        assertThat(toChangePercent(5, 10)).isEqualTo(-50);
        assertThat(toChangePercent(2, 3)).isEqualTo(-33);
        assertThat(toChangePercent(3, 0)).isNull();
    }
}
