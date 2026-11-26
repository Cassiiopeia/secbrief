package kr.suhsaechan.secbrief.application.notification;

import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;

/**
 * 알림 판정 대상 1건: 기사 + 긴급 알림 종류(알릴 만한 긴급이 아니면 null) + 관심 판정 값(분류 code, 주요)
 * 긴급 알림 종류는 UrgentAlertPolicy (#97 AI 판단 전 고위험은 규칙으로만)
 */
public record AlertCandidate(Article article, AlertCategory urgentCategory, boolean isUrgent, boolean isNews,
                             String productCode, String fieldCode, String ecosystemCode, boolean isNotable) {

    public static AlertCandidate from(Article article, TagCatalog catalog) {
        AlertCategory urgentCategory = article.isUrgent() && UrgentAlertPolicy.isAlertWorthy(article, catalog)
                ? UrgentAlertPolicy.toAlertCategory(article)
                : null;
        return new AlertCandidate(article, urgentCategory, article.isUrgent(), article.getKind() == ArticleKind.NEWS,
                article.getProductCode(), article.getFieldCode(), article.getEcosystemCode(), article.isNotable());
    }

    public long articleId() {
        return article.getId();
    }
}
