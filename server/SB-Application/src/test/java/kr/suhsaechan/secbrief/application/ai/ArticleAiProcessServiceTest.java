package kr.suhsaechan.secbrief.application.ai;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AI 묶음 처리: 키 없으면 건너뜀, 건별 성공·실패 기록, 묶음 크기만큼만 조회
 */
class ArticleAiProcessServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final int BATCH_SIZE = 20;

    private final FakeArticleTitleAnalyzer fakeAnalyzer = new FakeArticleTitleAnalyzer();
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleAiResultRecorder recorder = mock(ArticleAiResultRecorder.class);
    private final ArticleAiProcessService service =
            new ArticleAiProcessService(fakeAnalyzer, articleRepository, recorder, BATCH_SIZE);

    @Test
    void skipsWhenApiKeyMissing() {
        fakeAnalyzer.setAvailable(false);

        assertThat(service.processPendingArticles().isSkipped()).isTrue();
        verifyNoInteractions(articleRepository);
    }

    @Test
    void recordsSuccessForEachPendingArticle() {
        ArticleTitleAnalysis analysis = new ArticleTitleAnalysis(ArticleTopic.RANSOMWARE, "랜섬웨어 공격");
        fakeAnalyzer.respondTo("First headline", analysis);
        fakeAnalyzer.respondTo("Second headline", analysis);
        givenPendingArticles(createArticle(1L, "First headline"), createArticle(2L, "Second headline"));

        AiProcessResult result = service.processPendingArticles();

        assertThat(result).isEqualTo(new AiProcessResult(2, 0, false));
        verify(recorder).recordSuccess(1L, analysis);
        verify(recorder).recordSuccess(2L, analysis);
    }

    @Test
    void failureOfOneArticleDoesNotStopOthers() {
        ArticleTitleAnalysis analysis = new ArticleTitleAnalysis(ArticleTopic.VULNERABILITY, null);
        fakeAnalyzer.respondTo("Answered headline", analysis);
        givenPendingArticles(createArticle(1L, "Unanswered headline"), createArticle(2L, "Answered headline"));

        AiProcessResult result = service.processPendingArticles();

        assertThat(result).isEqualTo(new AiProcessResult(1, 1, false));
        verify(recorder).recordFailure(1L);
        verify(recorder).recordSuccess(2L, analysis);
    }

    @Test
    void readsAtMostBatchSize() {
        givenPendingArticles();

        service.processPendingArticles();

        verify(articleRepository).findByAiStatusOrderByIdAsc(AiStatus.PENDING, Limit.of(BATCH_SIZE));
    }

    private void givenPendingArticles(Article... articles) {
        when(articleRepository.findByAiStatusOrderByIdAsc(eq(AiStatus.PENDING), any())).thenReturn(List.of(articles));
    }

    private static Article createArticle(Long id, String title) {
        Article article = Article.create(
                createSource(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleRegion.OVERSEAS, FeedFormat.RSS, null),
                new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, title, "https://thehackernews.com/" + id,
                        "The Hacker News", NOW, false, null, null, null, false, false),
                NOW);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }
}
