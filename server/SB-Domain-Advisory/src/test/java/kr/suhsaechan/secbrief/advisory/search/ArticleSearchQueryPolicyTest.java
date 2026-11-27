package kr.suhsaechan.secbrief.advisory.search;

import static kr.suhsaechan.secbrief.advisory.search.ArticleSearchQueryPolicy.isCveIdQuery;
import static kr.suhsaechan.secbrief.advisory.search.ArticleSearchQueryPolicy.toSearchSize;
import static kr.suhsaechan.secbrief.advisory.search.ArticleSearchQueryPolicy.toSearchText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 검색 요청 규칙: 질의 trim 후 2~100자, 크기 기본 30·1~50, 벗어나면 INVALID_REQUEST (질의는 메시지에 없음), CVE 번호 질의 판정
 */
class ArticleSearchQueryPolicyTest {

    @Test
    void trimsQueryAndAcceptsTwoToHundredCharacters() {
        assertThat(toSearchText("  랜섬  ")).isEqualTo("랜섬");
        assertThat(toSearchText("가".repeat(100))).hasSize(100);
    }

    @Test
    void rejectsTooShortOrTooLongQueryWithoutEchoingIt() {
        assertThatThrownBy(() -> toSearchText(" 가 ")).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> toSearchText("   ")).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> toSearchText(null)).extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> toSearchText("비".repeat(101)))
                .hasMessageNotContaining("비비")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void sizeDefaultsToThirtyAndClampsOutOfRange() {
        assertThat(toSearchSize(null)).isEqualTo(30);
        assertThat(toSearchSize(1)).isEqualTo(1);
        assertThat(toSearchSize(50)).isEqualTo(50);
        assertThat(toSearchSize(0)).isEqualTo(30);
        assertThat(toSearchSize(51)).isEqualTo(50);
    }

    @Test
    void detectsCveIdQueryIgnoringCase() {
        assertThat(isCveIdQuery("CVE-2026-5555")).isTrue();
        assertThat(isCveIdQuery("cve-2026")).isTrue();
        assertThat(isCveIdQuery("Next.js CVE")).isFalse();
    }
}
