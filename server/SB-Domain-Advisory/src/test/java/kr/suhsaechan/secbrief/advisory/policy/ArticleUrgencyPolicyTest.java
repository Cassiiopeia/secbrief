package kr.suhsaechan.secbrief.advisory.policy;

import static kr.suhsaechan.secbrief.advisory.policy.ArticleUrgencyPolicy.isUrgent;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import org.junit.jupiter.api.Test;

/**
 * 긴급 판정 규칙: CVSS 9.0 이상, KEV 등재, KISA 공지 제목의 '긴급' 중 하나
 */
class ArticleUrgencyPolicyTest {

    @Test
    void cvssEightNineIsNotUrgent() {
        assertThat(isUrgent(SourceCode.NVD, "x", new BigDecimal("8.9"), false)).isFalse();
    }

    @Test
    void cvssNineZeroIsUrgent() {
        assertThat(isUrgent(SourceCode.NVD, "x", new BigDecimal("9.0"), false)).isTrue();
    }

    @Test
    void ghsaCriticalScoreIsUrgent() {
        assertThat(isUrgent(SourceCode.GHSA, "x", new BigDecimal("9.3"), false)).isTrue();
    }

    @Test
    void kevIsUrgentWithoutScore() {
        assertThat(isUrgent(SourceCode.CISA_KEV, "x", null, true)).isTrue();
    }

    @Test
    void kisaTitleWithUrgentKeywordIsUrgent() {
        assertThat(isUrgent(SourceCode.KISA, "[긴급] Ivanti 보안 업데이트 권고", null, false)).isTrue();
    }

    @Test
    void urgentKeywordFromNonKisaSourceIsNotUrgent() {
        assertThat(isUrgent(SourceCode.DAILYSECU, "긴급 점검", null, false)).isFalse();
    }

    @Test
    void kisaTitleWithoutUrgentKeywordIsNotUrgent() {
        assertThat(isUrgent(SourceCode.KISA, "정기 보안 업데이트 안내", null, false)).isFalse();
    }

    @Test
    void missingScoreAndTitleAreNotUrgent() {
        assertThat(isUrgent(SourceCode.KISA, null, null, false)).isFalse();
    }
}
