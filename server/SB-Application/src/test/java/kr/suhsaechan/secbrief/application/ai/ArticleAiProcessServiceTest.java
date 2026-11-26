package kr.suhsaechan.secbrief.application.ai;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
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
 * AI 묶음 처리: 제공자 미설정이면 건너뜀, 건별 성공·실패 기록, 제공자 호출 실패는 기록 없이 틱을 멈춤, 호출당 건수로 나눔
 */
class ArticleAiProcessServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final int BATCH_SIZE = 20;
    private static final int REQUEST_BATCH_SIZE = 2;
    private static final ArticleTitleAnalysis ANALYSIS = new ArticleTitleAnalysis(ArticleTopic.RANSOMWARE, "랜섬웨어 공격", ArticleImportance.ROUTINE, null);

    private final FakeArticleTitleAnalyzer fakeAnalyzer = new FakeArticleTitleAnalyzer();
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleAiResultRecorder recorder = mock(ArticleAiResultRecorder.class);
    private final ArticleAiProcessService service =
            new ArticleAiProcessService(fakeAnalyzer, articleRepository, recorder, BATCH_SIZE, REQUEST_BATCH_SIZE);

    @Test
    void skipsWhenProviderNotConfigured() {
        fakeAnalyzer.setAvailable(false);

        assertThat(service.processPendingArticles().isSkipped()).isTrue();
        verifyNoInteractions(articleRepository);
    }

    @Test
    void recordsSuccessForEachPendingArticle() {
        fakeAnalyzer.respondTo("First headline", ANALYSIS);
        fakeAnalyzer.respondTo("Second headline", ANALYSIS);
        givenPendingArticles(createArticle(1L, "First headline"), createArticle(2L, "Second headline"));

        AiProcessResult result = service.processPendingArticles();

        assertThat(result).isEqualTo(new AiProcessResult(2, 0, false));
        verify(recorder).recordSuccess(1L, ANALYSIS);
        verify(recorder).recordSuccess(2L, ANALYSIS);
    }

    @Test
    void failedOutcomeInBatchDoesNotFailOthers() {
        fakeAnalyzer.respondTo("Answered headline", ANALYSIS);
        givenPendingArticles(createArticle(1L, "Unanswered headline"), createArticle(2L, "Answered headline"));

        AiProcessResult result = service.processPendingArticles();

        assertThat(result).isEqualTo(new AiProcessResult(1, 1, false));
        verify(recorder).recordFailure(1L);
        verify(recorder).recordSuccess(2L, ANALYSIS);
    }

    @Test
    void splitsPendingArticlesByRequestBatchSize() {
        givenPendingArticles(createArticle(1L, "A"), createArticle(2L, "B"), createArticle(3L, "C"),
                createArticle(4L, "D"), createArticle(5L, "E"));

        service.processPendingArticles();

        assertThat(fakeAnalyzer.getReceivedBatchSizes()).containsExactly(2, 2, 1);
    }

    @Test
    void providerCallFailureKeepsArticlesPendingAndStopsTick() {
        // 제공자 장애: 시도 횟수를 쓰지 않고(기록 없음) 남은 묶음도 부르지 않음
        fakeAnalyzer.respondTo("Third headline", ANALYSIS);
        fakeAnalyzer.failCallContaining("Second headline");
        givenPendingArticles(createArticle(1L, "First headline"), createArticle(2L, "Second headline"),
                createArticle(3L, "Third headline"));

        AiProcessResult result = service.processPendingArticles();

        assertThat(result).isEqualTo(new AiProcessResult(0, 0, false));
        assertThat(fakeAnalyzer.getReceivedBatchSizes()).hasSize(1);
        verify(recorder, never()).recordFailure(org.mockito.ArgumentMatchers.anyLong());
        verify(recorder, never()).recordSuccess(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordsFailureWhenSuccessRecordThrows() {
        fakeAnalyzer.respondTo("First headline", ANALYSIS);
        fakeAnalyzer.respondTo("Second headline", ANALYSIS);
        givenPendingArticles(createArticle(1L, "First headline"), createArticle(2L, "Second headline"));
        doThrow(new IllegalStateException("db")).when(recorder).recordSuccess(1L, ANALYSIS);

        AiProcessResult result = service.processPendingArticles();

        assertThat(result).isEqualTo(new AiProcessResult(1, 1, false));
        verify(recorder).recordFailure(1L);
    }

    @Test
    void treatsNonPositiveRequestBatchSizeAsOne() {
        ArticleAiProcessService oneByOneService =
                new ArticleAiProcessService(fakeAnalyzer, articleRepository, recorder, BATCH_SIZE, 0);
        givenPendingArticles(createArticle(1L, "A"), createArticle(2L, "B"));

        oneByOneService.processPendingArticles();

        assertThat(fakeAnalyzer.getReceivedBatchSizes()).containsExactly(1, 1);
    }

    @Test
    void readsAtMostBatchSize() {
        givenPendingArticles();

        service.processPendingArticles();

        verify(articleRepository).findByAiStatusOrderByIdAsc(AiStatus.PENDING, Limit.of(BATCH_SIZE));
        assertThat(fakeAnalyzer.getReceivedBatchSizes()).isEmpty();
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
