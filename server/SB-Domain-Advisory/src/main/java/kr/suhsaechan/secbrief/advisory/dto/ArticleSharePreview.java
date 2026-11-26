package kr.suhsaechan.secbrief.advisory.dto;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ImageStatus;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.ArticleSummaryState;

/**
 * 공유 미리보기 페이지용 기사 1건: 한국어 제목(없으면 원문 제목), 직접 쓴 한 줄 결론·무슨 일(브리핑 있을 때만), 출처·게시일, 원문 링크
 * 기사 본문·요약 입력은 싣지 않음 (서비스 원칙 3)
 */
public record ArticleSharePreview(
        Long articleId,
        String title,
        // 번역 제목을 보여 줄 때의 원문 제목 (번역이 없으면 null)
        String originalTitle,
        // 브리핑이 없으면 둘 다 null
        String oneLine,
        String whatHappened,
        String sourceName,
        Instant publishedAt,
        String originalUrl,
        boolean hasImage
) {

    public static ArticleSharePreview from(Article article) {
        boolean hasTranslatedTitle = article.getTranslatedTitle() != null && !article.getTranslatedTitle().isBlank();
        ArticleSummaryState summaryState = article.getSummaryState();
        boolean hasBrief = summaryState.hasBrief();
        return new ArticleSharePreview(
                article.getId(),
                hasTranslatedTitle ? article.getTranslatedTitle() : article.getTitle(),
                hasTranslatedTitle ? article.getTitle() : null,
                hasBrief ? summaryState.getOneLine() : null,
                hasBrief ? summaryState.getWhatHappened() : null,
                toSourceName(article),
                article.getPublishedAt(),
                article.getOriginalUrl(),
                article.getImageState().getStatus() == ImageStatus.STORED);
    }

    // 게시자 이름(앱 상세와 같은 값) > 없으면 출처 표시 이름
    private static String toSourceName(Article article) {
        String publisherName = article.getPublisherName();
        return publisherName != null && !publisherName.isBlank() ? publisherName : article.getSource().getDisplayName();
    }
}
