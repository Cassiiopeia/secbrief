package kr.suhsaechan.secbrief.advisory.classification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.support.CatalogSeedFixtures;
import org.junit.jupiter.api.Test;

/**
 * 주요 판정: KEV, 널리 쓰는 제품, CVSS 9.0 이상 + AI 판단 끝 + 일반 아님, 헤드라인 (하나라도)
 */
class ArticleNotablePolicyTest {

    private static final TagCatalog CATALOG = CatalogSeedFixtures.seedCatalog();
    private static final BigDecimal CRITICAL_SCORE = new BigDecimal("9.8");
    private static final BigDecimal HIGH_SCORE = new BigDecimal("8.8");

    @Test
    void isNotableWhenAnyRuleMatches() {
        assertThat(ArticleNotablePolicy.isNotable(true, null, null, AiStatus.PENDING, ArticleImportance.ROUTINE, CATALOG)).isTrue();
        assertThat(ArticleNotablePolicy.isNotable(false, "gradle", HIGH_SCORE, AiStatus.DONE, ArticleImportance.ROUTINE, CATALOG))
                .isTrue();
        assertThat(ArticleNotablePolicy.isNotable(false, null, CRITICAL_SCORE, AiStatus.DONE, ArticleImportance.NOTABLE, CATALOG))
                .isTrue();
        assertThat(ArticleNotablePolicy.isNotable(false, null, null, AiStatus.DONE, ArticleImportance.HEADLINE, CATALOG)).isTrue();
    }

    @Test
    void isNotNotableForNichePackageOrRoutineCriticalOrUnjudgedCritical() {
        // 처음 보는 패키지 고위험, AI 가 일반으로 본 9.8, AI 판단 전 9.8, 널리 쓰지 않는 제품(WordPress 플러그인)
        assertThat(ArticleNotablePolicy.isNotable(false, null, HIGH_SCORE, AiStatus.DONE, ArticleImportance.NOTABLE, CATALOG))
                .isFalse();
        assertThat(ArticleNotablePolicy.isNotable(false, null, CRITICAL_SCORE, AiStatus.DONE, ArticleImportance.ROUTINE, CATALOG))
                .isFalse();
        assertThat(ArticleNotablePolicy.isNotable(false, null, CRITICAL_SCORE, AiStatus.PENDING, ArticleImportance.ROUTINE, CATALOG))
                .isFalse();
        assertThat(ArticleNotablePolicy.isNotable(false, "wordpress", CRITICAL_SCORE, AiStatus.DONE, ArticleImportance.ROUTINE,
                CATALOG)).isFalse();
    }

    @Test
    void rulesOnlyFallbackUsesKevAndWidelyUsedProduct() {
        assertThat(ArticleNotablePolicy.isNotableByRulesOnly(false, "vmware", CATALOG)).isTrue();
        assertThat(ArticleNotablePolicy.isNotableByRulesOnly(false, null, CATALOG)).isFalse();
        assertThat(ArticleNotablePolicy.isNotableByRulesOnly(false, "removed-product", CATALOG)).isFalse();
    }
}
