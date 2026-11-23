package kr.suhsaechan.secbrief.advisory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.entity.Article;

/**
 * 기사 응답: 원문 제목 + AI 한국어 제목(없으면 null), 원문 링크·게시자·게시일, CVE 정보 (AI 상태는 내보내지 않음)
 */
public record ArticleResponse(
        Long id,
        ArticleKind kind,
        ArticleRegion region,
        ArticleTopic topic,
        String title,
        String translatedTitle,
        String originalUrl,
        String publisherName,
        Instant publishedAt,
        // boolean 은 is 접두사 그대로 JSON 이름으로 (앱 모델과 같은 이름)
        @JsonProperty("isPublishedAtEstimated") boolean isPublishedAtEstimated,
        String cveId,
        BigDecimal cvssScore,
        String cvssVersion,
        @JsonProperty("isKev") boolean isKev,
        @JsonProperty("isUrgent") boolean isUrgent
) {

    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getKind(),
                article.getRegion(),
                article.getTopic(),
                article.getTitle(),
                article.getTranslatedTitle(),
                article.getOriginalUrl(),
                article.getPublisherName(),
                article.getPublishedAt(),
                article.isPublishedAtEstimated(),
                article.getCveId(),
                article.getCvssScore(),
                article.getCvssVersion(),
                article.isKev(),
                article.isUrgent());
    }
}
