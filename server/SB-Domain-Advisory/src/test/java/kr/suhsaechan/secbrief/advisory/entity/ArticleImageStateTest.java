package kr.suhsaechan.secbrief.advisory.entity;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.ImageStatus;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import org.junit.jupiter.api.Test;

/**
 * 기사 이미지 상태 전이: 주소 있음 > PENDING / 없음 > NONE, 저장 > STORED + 키, 실패 1·2회 > PENDING, 3회 > FAILED, NVD 링크 교체에도 유지
 */
class ArticleImageStateTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final String IMAGE_SOURCE_URL = "https://images.example.test/cover.jpg";
    private static final String IMAGE_OBJECT_KEY = "articles/2026/11/1.jpg";

    private final Article article = createArticle(IMAGE_SOURCE_URL);

    @Test
    void startsPendingWhenDraftHasImageUrl() {
        assertThat(article.getImageState().getStatus()).isEqualTo(ImageStatus.PENDING);
        assertThat(article.getImageState().getSourceUrl()).isEqualTo(IMAGE_SOURCE_URL);
        assertThat(article.getImageState().getObjectKey()).isNull();
        assertThat(article.getImageState().getAttemptCount()).isZero();
    }

    @Test
    void startsNoneWhenDraftHasNoImageUrl() {
        Article articleWithoutImage = createArticle(null);

        assertThat(articleWithoutImage.getImageState().getStatus()).isEqualTo(ImageStatus.NONE);
        assertThat(articleWithoutImage.getImageState().getSourceUrl()).isNull();
    }

    @Test
    void marksStoredWithObjectKey() {
        article.getImageState().markStored(IMAGE_OBJECT_KEY);

        assertThat(article.getImageState().getStatus()).isEqualTo(ImageStatus.STORED);
        assertThat(article.getImageState().getObjectKey()).isEqualTo(IMAGE_OBJECT_KEY);
    }

    @Test
    void staysPendingAfterFirstAndSecondFailure() {
        article.getImageState().recordFailure();
        article.getImageState().recordFailure();

        assertThat(article.getImageState().getStatus()).isEqualTo(ImageStatus.PENDING);
        assertThat(article.getImageState().getAttemptCount()).isEqualTo(2);
    }

    @Test
    void marksFailedAfterThirdFailure() {
        article.getImageState().recordFailure();
        article.getImageState().recordFailure();
        article.getImageState().recordFailure();

        assertThat(article.getImageState().getStatus()).isEqualTo(ImageStatus.FAILED);
        assertThat(article.getImageState().getAttemptCount()).isEqualTo(3);
        assertThat(article.getImageState().getObjectKey()).isNull();
    }

    @Test
    void keepsImageWhenLinkIsReplacedWithNvd() {
        article.getImageState().markStored(IMAGE_OBJECT_KEY);

        article.replaceLinkWithNvd(
                createSource(SourceCode.NVD, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.NVD_API, null),
                new ArticleDraft(ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, "CVE-2026-0001: Example flaw",
                        "https://nvd.nist.gov/vuln/detail/CVE-2026-0001", "NVD", NOW, false, "CVE-2026-0001", null, null,
                        false, false));

        assertThat(article.getImageState().getStatus()).isEqualTo(ImageStatus.STORED);
        assertThat(article.getImageState().getObjectKey()).isEqualTo(IMAGE_OBJECT_KEY);
        assertThat(article.getImageState().getSourceUrl()).isEqualTo(IMAGE_SOURCE_URL);
    }

    private static Article createArticle(String imageSourceUrl) {
        return Article.create(
                createSource(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleRegion.OVERSEAS, FeedFormat.RSS, null),
                new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, "Ransomware gang hits hospital",
                        "https://thehackernews.com/2026/11/example.html", "The Hacker News", NOW, false,
                        null, null, null, false, false, imageSourceUrl),
                NOW);
    }
}
