package kr.suhsaechan.secbrief.advisory.ai;

import static kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPolicy.toAcceptedTranslation;
import static kr.suhsaechan.secbrief.advisory.constant.ArticleRegion.DOMESTIC;
import static kr.suhsaechan.secbrief.advisory.constant.ArticleRegion.OVERSEAS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 번역 검증: CVE·버전 보존 통과, 지어낸 숫자·바뀐 버전·국내·빈 값·원문과 같음은 버림
 */
class ArticleTitleAnalysisPolicyTest {

    @Test
    void acceptsTranslationKeepingCveAndVersion() {
        assertThat(toAcceptedTranslation("Critical RCE in Next.js 15.2.3 (CVE-2026-5555)", OVERSEAS,
                "  Next.js 15.2.3 원격 코드 실행 심각 취약점 (CVE-2026-5555) "))
                .isEqualTo("Next.js 15.2.3 원격 코드 실행 심각 취약점 (CVE-2026-5555)");
    }

    @Test
    void dropsTranslationWithDigitsNotInOriginal() {
        assertThat(toAcceptedTranslation("Ivanti patches flaws", OVERSEAS, "Ivanti, 취약점 3개 패치")).isNull();
    }

    @Test
    void dropsTranslationWithChangedVersion() {
        assertThat(toAcceptedTranslation("Apache Tomcat 10.1.34 fixes RCE", OVERSEAS, "Apache Tomcat 10.1.33 원격 코드 실행 수정")).isNull();
    }

    @Test
    void dropsTranslationForDomesticItem() {
        assertThat(toAcceptedTranslation("랜섬웨어 피해 증가", DOMESTIC, "랜섬웨어 피해 증가")).isNull();
    }

    @Test
    void dropsBlankOrSameAsOriginal() {
        assertThat(toAcceptedTranslation("Example flaw", OVERSEAS, "  ")).isNull();
        assertThat(toAcceptedTranslation("Example flaw", OVERSEAS, null)).isNull();
        assertThat(toAcceptedTranslation("Example flaw", OVERSEAS, "Example flaw")).isNull();
    }

    @Test
    void dropsTranslationLongerThan500Characters() {
        assertThat(toAcceptedTranslation("Example flaw", OVERSEAS, "가".repeat(501))).isNull();
    }
}
