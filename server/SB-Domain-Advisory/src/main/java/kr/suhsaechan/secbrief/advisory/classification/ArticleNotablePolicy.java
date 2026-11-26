package kr.suhsaechan.secbrief.advisory.classification;

import java.math.BigDecimal;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogEntry;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.advisory.policy.ArticleUrgencyPolicy;

/**
 * 주요 판정 (하나라도 맞으면): KEV 등재, 널리 쓰는 제품, CVSS 9.0 이상이면서 AI 판단이 끝났고 일반이 아님, 헤드라인(AI 또는 규칙 하한)
 * 목록 기본값(오픈소스 주요만)과 관심 분야·생태계 알림의 기준
 */
public final class ArticleNotablePolicy {

    private ArticleNotablePolicy() {
    }

    public static boolean isNotable(boolean isKev, String productCode, BigDecimal cvssScore, AiStatus aiStatus,
                                    ArticleImportance importance, TagCatalog catalog) {
        return isKev
                || isWidelyUsedProduct(productCode, catalog)
                || (isCriticalScore(cvssScore) && aiStatus == AiStatus.DONE && importance != ArticleImportance.ROUTINE)
                || importance == ArticleImportance.HEADLINE;
    }

    /** 규칙만의 주요 판정 (AI 판단을 기다리다 넘긴 고위험 알림용, #97): KEV 또는 널리 쓰는 제품 */
    public static boolean isNotableByRulesOnly(boolean isKev, String productCode, TagCatalog catalog) {
        return isKev || isWidelyUsedProduct(productCode, catalog);
    }

    public static boolean isWidelyUsedProduct(String productCode, TagCatalog catalog) {
        return catalog.find(CatalogTagKind.PRODUCT, productCode).map(CatalogEntry::isWidelyUsed).orElse(false);
    }

    private static boolean isCriticalScore(BigDecimal cvssScore) {
        return cvssScore != null && cvssScore.compareTo(ArticleUrgencyPolicy.URGENT_CVSS_THRESHOLD) >= 0;
    }
}
