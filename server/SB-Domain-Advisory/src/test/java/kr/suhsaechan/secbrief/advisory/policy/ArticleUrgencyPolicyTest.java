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
    void ghsaHighSeverityScoreIsNotUrgent() {
        // high 등급(7.0~8.9)은 등급이 아니라 점수 규칙으로 판정: 긴급 아님
        assertThat(isUrgent(SourceCode.GHSA_HIGH, "x", new BigDecimal("8.8"), false)).isFalse();
        assertThat(isUrgent(SourceCode.GHSA_HIGH, "x", null, false)).isFalse();
    }

    @Test
    void ghsaHighBecomesUrgentOnlyByExistingRules() {
        assertThat(isUrgent(SourceCode.GHSA_HIGH, "x", new BigDecimal("9.0"), false)).isTrue();
        assertThat(isUrgent(SourceCode.GHSA_HIGH, "x", new BigDecimal("7.5"), true)).isTrue();
    }

    @Test
    void overseasDistributionAdvisoryIsNeverUrgentByTitle() {
        // 리눅스 배포판 권고 제목의 Critical·긴급은 KISA 공지가 아니라 긴급으로 보지 않음
        assertThat(isUrgent(SourceCode.REDHAT_RHSA, "RHSA-2026:103537: Critical: Satellite 6.16 EUS security update", null, false)).isFalse();
        assertThat(isUrgent(SourceCode.UBUNTU_USN, "USN-9203-1: libpcap vulnerabilities 긴급", null, false)).isFalse();
        assertThat(SourceCode.isKisa(SourceCode.DEBIAN_DSA)).isFalse();
        assertThat(SourceCode.isKisa(SourceCode.NODEJS_SECURITY)).isFalse();
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
