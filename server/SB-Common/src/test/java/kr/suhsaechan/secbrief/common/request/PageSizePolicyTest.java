package kr.suhsaechan.secbrief.common.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 쪽 크기 조정: null·0·음수 > 기본값, 범위 안 그대로, 최대 초과 > 최대값
 */
class PageSizePolicyTest {

    @Test
    void usesDefaultForMissingOrNonPositiveSize() {
        assertThat(PageSizePolicy.toClampedPageSize(null, 20, 50)).isEqualTo(20);
        assertThat(PageSizePolicy.toClampedPageSize(0, 20, 50)).isEqualTo(20);
        assertThat(PageSizePolicy.toClampedPageSize(-3, 20, 50)).isEqualTo(20);
    }

    @Test
    void keepsSizeInRangeAndCapsAtMaximum() {
        assertThat(PageSizePolicy.toClampedPageSize(1, 20, 50)).isEqualTo(1);
        assertThat(PageSizePolicy.toClampedPageSize(50, 20, 50)).isEqualTo(50);
        assertThat(PageSizePolicy.toClampedPageSize(51, 20, 50)).isEqualTo(50);
    }
}
