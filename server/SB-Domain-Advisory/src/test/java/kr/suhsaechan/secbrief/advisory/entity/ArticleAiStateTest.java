package kr.suhsaechan.secbrief.advisory.entity;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import org.junit.jupiter.api.Test;

/**
 * 기사 AI 상태 전이: 성공 > DONE, 실패 1·2회 > PENDING, 3회 > FAILED
 */
class ArticleAiStateTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    private final Article article = Article.create(
            createSource(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleRegion.OVERSEAS, FeedFormat.RSS, null),
            new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, "Ransomware gang hits hospital",
                    "https://thehackernews.com/2026/11/example.html", "The Hacker News", NOW, false,
                    null, null, null, false, false),
            NOW);

    @Test
    void appliesAnalysisAndMarksDone() {
        article.applyAiAnalysis(ArticleTopic.RANSOMWARE, "번역");

        assertThat(article.getTopic()).isEqualTo(ArticleTopic.RANSOMWARE);
        assertThat(article.getTranslatedTitle()).isEqualTo("번역");
        assertThat(article.getAiStatus()).isEqualTo(AiStatus.DONE);
    }

    @Test
    void staysPendingAfterFirstAndSecondFailure() {
        article.recordAiFailure();
        article.recordAiFailure();

        assertThat(article.getAiStatus()).isEqualTo(AiStatus.PENDING);
        assertThat(article.getAiAttemptCount()).isEqualTo(2);
        assertThat(article.getTopic()).isEqualTo(ArticleTopic.GENERAL);
    }

    @Test
    void marksFailedAfterThirdFailure() {
        article.recordAiFailure();
        article.recordAiFailure();
        article.recordAiFailure();

        assertThat(article.getAiStatus()).isEqualTo(AiStatus.FAILED);
        assertThat(article.getAiAttemptCount()).isEqualTo(3);
        assertThat(article.getTranslatedTitle()).isNull();
    }
}
