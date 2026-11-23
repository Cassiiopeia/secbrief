package kr.suhsaechan.secbrief.advisory.policy;

import java.math.BigDecimal;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;

/**
 * 정규화를 마친 기사 초안: 저장 직전 값 (주제는 없음, 저장 시 GENERAL·PENDING)
 */
public record ArticleDraft(
        ArticleKind kind,
        ArticleRegion region,
        String title,
        String originalUrl,
        String publisherName,
        Instant publishedAt,
        boolean isPublishedAtEstimated,
        String cveId,
        BigDecimal cvssScore,
        String cvssVersion,
        boolean isKev,
        boolean isUrgent
) {
}
