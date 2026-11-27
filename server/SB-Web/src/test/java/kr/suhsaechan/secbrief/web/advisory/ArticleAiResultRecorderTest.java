package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.application.ai.ArticleAiResultRecorder;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.ActiveProfiles;

/**
 * AI 결과 기록 (DB): 지어낸 숫자 번역은 버리고 주제만 반영, 3회 실패면 대기열에서 빠짐
 */
@SpringBootTest
@ActiveProfiles("test")
class ArticleAiResultRecorderTest extends PostgresContainerTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");

    @Autowired
    private ArticleAiResultRecorder recorder;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private SourceRepository sourceRepository;

    private Long articleId;

    @BeforeEach
    void saveOverseasArticle() {
        articleRepository.deleteAll();
        Source source = sourceRepository.findByCode(SourceCode.THE_HACKER_NEWS).orElseThrow();
        Article article = Article.create(source, new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS,
                "Ivanti patches flaws", "https://thehackernews.com/2026/11/ivanti.html", "The Hacker News",
                NOW, false, null, null, null, false, false), NOW);
        articleId = articleRepository.save(article).getId();
    }

    @Test
    void dropsInventedNumberButKeepsTopic() {
        recorder.recordSuccess(articleId, new ArticleTitleAnalysis(ArticleTopic.VULNERABILITY, "Ivanti 취약점 3개 패치", ArticleImportance.ROUTINE, null));

        Article article = articleRepository.findById(articleId).orElseThrow();
        assertThat(article.getAiAnalysisState().getTopic()).isEqualTo(ArticleTopic.VULNERABILITY);
        assertThat(article.getAiAnalysisState().getTranslatedTitle()).isNull();
        assertThat(article.getAiAnalysisState().getStatus()).isEqualTo(AiStatus.DONE);
    }

    @Test
    void marksFailedAfterThirdFailure() {
        recorder.recordFailure(articleId);
        recorder.recordFailure(articleId);
        recorder.recordFailure(articleId);

        assertThat(articleRepository.findById(articleId).orElseThrow().getAiAnalysisState().getStatus()).isEqualTo(AiStatus.FAILED);
        assertThat(articleRepository.findByAiAnalysisState_StatusOrderByPublishedAtDescIdDesc(AiStatus.PENDING, Limit.of(20))).isEmpty();
    }
}
