package kr.suhsaechan.secbrief.advisory.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 기사 목록 쪽 크기: 없음·1 미만 > 20, 50 초과 > 50 (거부하지 않음)
 */
class ArticleSearchConditionTest {

    @Test
    void clampsPageSizeInsteadOfRejecting() {
        assertThat(withSize(null).toPageSize()).isEqualTo(20);
        assertThat(withSize(0).toPageSize()).isEqualTo(20);
        assertThat(withSize(5).toPageSize()).isEqualTo(5);
        assertThat(withSize(51).toPageSize()).isEqualTo(50);
    }

    private static ArticleSearchCondition withSize(Integer size) {
        return new ArticleSearchCondition(null, null, null, null, null, null, null, size);
    }
}
