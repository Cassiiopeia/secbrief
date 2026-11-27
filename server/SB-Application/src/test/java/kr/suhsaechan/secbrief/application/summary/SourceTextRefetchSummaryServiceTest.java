package kr.suhsaechan.secbrief.application.summary;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchMethod;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchResult;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetchTarget;
import kr.suhsaechan.secbrief.advisory.refetch.SourceTextRefetcher;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryOutcome;
import kr.suhsaechan.secbrief.application.ai.AiDailyBudgetGuard;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 다시 요약 틱: 제공자 미설정이면 건너뜀, 다시 받은 입력만 요약, 브리핑 반영, 받지 못한 건은 제공자를 부르지 않음
 * 제공자 호출 실패면 그 건부터 다음 틱에, 회차 끝이면 다음 서울 날짜에 처음부터, 하루 대상 상한을 지킴
 */
class SourceTextRefetchSummaryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");
    private static final int BATCH_SIZE = 5;
    private static final int DAILY_ARTICLE_LIMIT = 20;
    private static final String SOURCE_TEXT = "Example Corp confirmed attackers exploited a flaw in Example Router 4.8.1.";
    private static final ArticleBrief BRIEF = new ArticleBrief("Example Router 결함이 악용되고 있어요.",
            "공격자가 Example Router 4.8.1의 결함을 악용했어요.", null, List.of(new AffectedTarget("Example Router", "4.8.1")),
            List.of());

    private final FakeArticleSummarizer fakeSummarizer = new FakeArticleSummarizer();
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final SourceTextRefetcher sourceTextRefetcher = mock(SourceTextRefetcher.class);
    private final ArticleSummaryResultRecorder recorder = mock(ArticleSummaryResultRecorder.class);
    private final AiDailyBudgetGuard aiDailyBudgetGuard = mock(AiDailyBudgetGuard.class);
    private final MutableClock clock = new MutableClock(NOW);

    @Test
    void skipsWhenProviderNotConfigured() {
        fakeSummarizer.setAvailable(false);

        assertThat(createService(DAILY_ARTICLE_LIMIT).processTick().isSkipped()).isTrue();
        verifyNoInteractions(articleRepository, sourceTextRefetcher);
    }

    @Test
    void summarizesRefetchedSourceTextAndRecordsBrief() {
        Article article = createArticle(30L, "Hackers exploit Example Router");
        givenTargets(Long.MAX_VALUE, article);
        when(sourceTextRefetcher.refetch(toRefetchTarget(article))).thenReturn(SourceTextRefetchResult.recovered(SOURCE_TEXT));
        fakeSummarizer.respondTo("Hackers exploit Example Router", ArticleSummaryOutcome.succeeded(BRIEF));
        when(recorder.recordRefetchedBrief(30L, BRIEF)).thenReturn(true);

        SourceTextRefetchSummaryResult tickResult = createService(DAILY_ARTICLE_LIMIT).processTick();

        assertThat(tickResult.doneCount()).isEqualTo(1);
        assertThat(fakeSummarizer.getReceivedRequests()).singleElement()
                .satisfies(request -> assertThat(request.sourceText()).isEqualTo(SOURCE_TEXT));
        verify(articleRepository).findSourceTextRefetchTargets(Long.MAX_VALUE,
                SourceTextRefetchMethod.toSupportedSourceCodes(), Limit.of(BATCH_SIZE));
    }

    @Test
    void doesNotCallProviderWhenSourceTextIsNotRecoveredAndMovesOn() {
        Article article = createArticle(30L, "Gone article");
        givenTargets(Long.MAX_VALUE, article);
        when(sourceTextRefetcher.refetch(any())).thenReturn(SourceTextRefetchResult.notFound("status=404"));
        SourceTextRefetchSummaryService service = createService(DAILY_ARTICLE_LIMIT);

        SourceTextRefetchSummaryResult tickResult = service.processTick();
        service.processTick();

        assertThat(tickResult.notRecoveredCount()).isEqualTo(1);
        assertThat(fakeSummarizer.getReceivedRequests()).isEmpty();
        verify(recorder, never()).recordRefetchedBrief(anyLong(), any());
        // 다음 틱은 그 건 다음(id 30 미만)부터
        verify(articleRepository).findSourceTextRefetchTargets(eq(30L), any(), any());
    }

    @Test
    void retriesSameArticleNextTickWhenProviderCallFails() {
        Article article = createArticle(30L, "Provider down");
        givenTargets(Long.MAX_VALUE, article);
        when(sourceTextRefetcher.refetch(any())).thenReturn(SourceTextRefetchResult.recovered(SOURCE_TEXT));
        fakeSummarizer.failCallFor("Provider down");
        SourceTextRefetchSummaryService service = createService(DAILY_ARTICLE_LIMIT);

        service.processTick();
        service.processTick();

        verify(recorder, never()).recordRefetchedBrief(anyLong(), any());
        verify(articleRepository, never()).findSourceTextRefetchTargets(eq(30L), any(), any());
    }

    @Test
    void waitsUntilNextSeoulDateAfterPassFinishesThenStartsFromNewest() {
        when(articleRepository.findSourceTextRefetchTargets(anyLong(), any(), any())).thenReturn(List.of());
        SourceTextRefetchSummaryService service = createService(DAILY_ARTICLE_LIMIT);

        service.processTick();
        service.processTick();
        clock.advance(Duration.ofDays(1));
        service.processTick();

        // 회차 끝 1번 + 다음 날 1번 (같은 날 두 번째 틱은 읽지 않음)
        verify(articleRepository, times(2))
                .findSourceTextRefetchTargets(eq(Long.MAX_VALUE), any(), any());
    }

    @Test
    void readsNoMoreThanRemainingDailyArticleCount() {
        givenTargets(Long.MAX_VALUE, createArticle(30L, "First"), createArticle(29L, "Second"));
        when(sourceTextRefetcher.refetch(any())).thenReturn(SourceTextRefetchResult.notFound("status=404"));
        SourceTextRefetchSummaryService service = createService(3);

        service.processTick();
        service.processTick();

        verify(articleRepository).findSourceTextRefetchTargets(eq(Long.MAX_VALUE), any(), eq(Limit.of(3)));
        verify(articleRepository).findSourceTextRefetchTargets(eq(29L), any(), eq(Limit.of(1)));
    }

    // 하루 예산은 남아 있는 상태
    private SourceTextRefetchSummaryService createService(int dailyArticleLimit) {
        return new SourceTextRefetchSummaryService(articleRepository, sourceTextRefetcher, fakeSummarizer, recorder,
                aiDailyBudgetGuard, clock, BATCH_SIZE, dailyArticleLimit);
    }

    private void givenTargets(long beforeId, Article... articles) {
        when(articleRepository.findSourceTextRefetchTargets(eq(beforeId), any(), any())).thenReturn(List.of(articles));
    }

    private static SourceTextRefetchTarget toRefetchTarget(Article article) {
        return new SourceTextRefetchTarget(SourceCode.BLEEPING_COMPUTER, article.getTitle(), article.getOriginalUrl(), null);
    }

    // 이전 구조 요약만 있던 기사처럼 요약 입력 없음
    private static Article createArticle(Long id, String title) {
        Article article = Article.create(
                createSource(SourceCode.BLEEPING_COMPUTER, ArticleKind.NEWS, ArticleRegion.OVERSEAS, FeedFormat.RSS, null),
                new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, title, "https://www.bleepingcomputer.com/news/" + id,
                        "BleepingComputer", NOW, false, null, null, null, false, false, null, null, List.of()),
                NOW);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    /** 테스트가 시각을 옮길 수 있는 시계 */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
