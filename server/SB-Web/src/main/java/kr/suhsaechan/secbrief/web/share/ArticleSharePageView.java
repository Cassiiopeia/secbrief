package kr.suhsaechan.secbrief.web.share;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSharePreview;

/**
 * 기사 미리보기 화면 값: 기사 + 공유 주소 + 미리보기 문구·그림 주소 + 게시일 표기 + 앱 열기 주소 2개
 */
public record ArticleSharePageView(
        ArticleSharePreview article,
        // 공유 주소 (canonical, og:url)
        String pageUrl,
        // 미리보기 설명: 한 줄 결론 > 없으면 원문 제목 > 제목
        String description,
        // og:image 절대 주소: 기사 대표 이미지 > 없으면 앱 아이콘
        String previewImageUrl,
        // 페이지 안 대표 이미지 경로 (없으면 null)
        String articleImagePath,
        String publishedDateLabel,
        String appOpenUrl,
        String androidIntentUrl
) {

    // 게시일 표기: 2026년 11월 23일 (한국 시간)
    private static final DateTimeFormatter PUBLISHED_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일").withZone(ZoneId.of("Asia/Seoul"));

    public static ArticleSharePageView of(ArticleSharePreview article, SharePageLinks sharePageLinks, ShareLinkProperties properties) {
        String articleImagePath = article.hasImage() ? ArticleResponse.toImagePath(article.articleId()) : null;
        return new ArticleSharePageView(
                article,
                sharePageLinks.toArticlePageUrl(article.articleId()),
                toDescription(article),
                articleImagePath != null ? properties.toPublicUrl(articleImagePath) : sharePageLinks.getAppIconUrl(),
                articleImagePath,
                article.publishedAt() == null ? null : PUBLISHED_DATE_FORMATTER.format(article.publishedAt()),
                sharePageLinks.toAppArticleUrl(article.articleId()),
                sharePageLinks.toAndroidArticleIntentUrl(article.articleId()));
    }

    public boolean hasArticleImage() {
        return articleImagePath != null;
    }

    private static String toDescription(ArticleSharePreview article) {
        if (article.oneLine() != null) {
            return article.oneLine();
        }
        return article.originalTitle() != null ? article.originalTitle() : article.title();
    }
}
