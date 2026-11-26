package kr.suhsaechan.secbrief.advisory.ai;

import static kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPolicy.toAcceptedKeywords;
import static kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPolicy.toAcceptedTranslation;
import static kr.suhsaechan.secbrief.advisory.constant.ArticleRegion.DOMESTIC;
import static kr.suhsaechan.secbrief.advisory.constant.ArticleRegion.OVERSEAS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 번역 검증: CVE·버전 보존 통과, 지어낸 숫자·바뀐 버전·국내·빈 값·원문과 같음은 버림
 * 키워드 검증: # 제거·공백 정리, 빈 값·40자 초과·중복(대소문자 무시)·원문에 없는 숫자열·CVE 번호 제외, 최대 5개
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

    @Test
    void cleansKeywordsRemovingHashSignAndExtraWhitespace() {
        assertThat(toAcceptedKeywords("Red Hat patches Linux kernel zero-day",
                List.of(" #제로데이 ", "Red   Hat", "#Linux")))
                .containsExactly("제로데이", "Red Hat", "Linux");
    }

    @Test
    void dropsBlankNullAndTooLongKeywords() {
        assertThat(toAcceptedKeywords("Example flaw", Arrays.asList("", "  ", "#", null, "가".repeat(41), "가".repeat(40))))
                .containsExactly("가".repeat(40));
    }

    @Test
    void dropsDuplicateKeywordsIgnoringCaseKeepingFirstSpelling() {
        assertThat(toAcceptedKeywords("Next.js flaw", List.of("Next.js", "next.JS", "랜섬웨어", "랜섬웨어")))
                .containsExactly("Next.js", "랜섬웨어");
    }

    @Test
    void keepsKeywordDigitsOnlyWhenTheyAreInOriginalTitle() {
        assertThat(toAcceptedKeywords("Microsoft 365 outage hits Log4j users", List.of("Microsoft 365", "Log4j", "Windows 11", "Log4Shell 2")))
                .containsExactly("Microsoft 365", "Log4j");
    }

    @Test
    void dropsCveIdKeywordEvenWhenItIsInTitle() {
        assertThat(toAcceptedKeywords("Critical RCE in Next.js 15.2.3 (CVE-2026-5555)", List.of("CVE-2026-5555", "cve-2026-5555", "Next.js")))
                .containsExactly("Next.js");
    }

    @Test
    void keepsAtMostFiveKeywordsInGivenOrder() {
        assertThat(toAcceptedKeywords("Example flaw", List.of("가", "나", "다", "", "라", "마", "바", "사")))
                .containsExactly("가", "나", "다", "라", "마");
    }

    @Test
    void nullKeywordListBecomesEmpty() {
        assertThat(toAcceptedKeywords("Example flaw", null)).isEmpty();
    }
}
