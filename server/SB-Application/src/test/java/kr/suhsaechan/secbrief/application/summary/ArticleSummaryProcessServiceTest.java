package kr.suhsaechan.secbrief.application.summary;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryOutcome;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryRequest;
import kr.suhsaechan.secbrief.advisory.summary.SummaryWritingKind;
import kr.suhsaechan.secbrief.application.ai.AiDailyBudgetGuard;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 요약 묶음 처리: 제공자 미설정이면 건너뜀, 한 호출에 1건, 건별 성공·실패 기록 (거부·검증 탈락 포함)
 * 고쳐 쓰기·글 종류·내용 없는 입력은 ArticleSummaryRepairProcessTest
 * 제공자 호출 실패는 기록 없이 틱을 멈춤, 하루 호출 상한·틱 시간 상한·하루 예산을 지킴, 건수 설정은 틱마다 설정 저장소에서 읽음
 */
class ArticleSummaryProcessServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final int BATCH_SIZE = 10;
    private static final long TICK_BUDGET_SECONDS = 90;
    private static final int DAILY_LIMIT = 300;
    private static final String SOURCE_TEXT = "Attackers are exploiting a flaw in Example VPN 2.4.0 to steal session tokens.";
    private static final ArticleBrief BRIEF =
            new ArticleBrief("Example VPN 결함으로 세션 토큰이 털리고 있어요.",
                    "공격자가 Example VPN 2.4.0의 결함을 악용하고 있어요.", null, List.of(new AffectedTarget("Example VPN", "2.4.0")),
                    List.of());

    private final FakeArticleSummarizer fakeSummarizer = new FakeArticleSummarizer();
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleSummaryResultRecorder recorder = mock(ArticleSummaryResultRecorder.class);
    private final MutableClock clock = new MutableClock(NOW);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final AiDailyBudgetGuard aiDailyBudgetGuard = mock(AiDailyBudgetGuard.class);
    private final ArticleSummaryProcessService service = createService(DAILY_LIMIT);

    @Test
    void skipsWhenProviderNotConfigured() {
        fakeSummarizer.setAvailable(false);

        assertThat(service.processPendingSummaries().isSkipped()).isTrue();
        verifyNoInteractions(articleRepository);
    }

    @Test
    void summarizesEachPendingArticleInItsOwnCallAndRecordsSuccess() {
        fakeSummarizer.respondTo("First headline", ArticleSummaryOutcome.succeeded(BRIEF));
        fakeSummarizer.respondTo("Second headline", ArticleSummaryOutcome.succeeded(BRIEF));
        givenPendingArticles(createArticle(1L, "First headline"), createArticle(2L, "Second headline"));
        when(recorder.recordSuccess(anyLong(), any(), any())).thenReturn(true);

        SummaryProcessResult summaryProcessResult = service.processPendingSummaries();

        assertThat(summaryProcessResult).isEqualTo(new SummaryProcessResult(2, 0, false));
        assertThat(fakeSummarizer.getReceivedRequests()).containsExactly(
                new ArticleSummaryRequest("First headline", ArticleRegion.OVERSEAS, ArticleKind.NEWS,
                        SummaryWritingKind.NEWS_ARTICLE, SOURCE_TEXT),
                new ArticleSummaryRequest("Second headline", ArticleRegion.OVERSEAS, ArticleKind.NEWS,
                        SummaryWritingKind.NEWS_ARTICLE, SOURCE_TEXT));
        verify(recorder).recordSuccess(1L, SOURCE_TEXT, BRIEF);
        verify(recorder).recordSuccess(2L, SOURCE_TEXT, BRIEF);
    }

    @Test
    void failedOutcomeRecordsFailureWithoutFailingOthers() {
        fakeSummarizer.respondTo("Refused headline", ArticleSummaryOutcome.failed("refused"));
        fakeSummarizer.respondTo("Answered headline", ArticleSummaryOutcome.succeeded(BRIEF));
        givenPendingArticles(createArticle(1L, "Refused headline"), createArticle(2L, "Answered headline"));
        when(recorder.recordSuccess(anyLong(), any(), any())).thenReturn(true);

        SummaryProcessResult summaryProcessResult = service.processPendingSummaries();

        assertThat(summaryProcessResult).isEqualTo(new SummaryProcessResult(1, 1, false));
        verify(recorder).recordFailure(1L, SOURCE_TEXT);
        verify(recorder).recordSuccess(2L, SOURCE_TEXT, BRIEF);
    }

    @Test
    void summaryWithInventedNumberIsRecordedAsFailure() {
        fakeSummarizer.respondTo("First headline", ArticleSummaryOutcome.succeeded(
                new ArticleBrief("Example VPN 결함으로 세션 토큰이 털리고 있어요.",
                        "Example VPN 2.4.1 버전으로 올려야 해요.", null, List.of(), List.of())));
        givenPendingArticles(createArticle(1L, "First headline"));

        SummaryProcessResult summaryProcessResult = service.processPendingSummaries();

        assertThat(summaryProcessResult).isEqualTo(new SummaryProcessResult(0, 1, false));
        verify(recorder).recordFailure(1L, SOURCE_TEXT);
        verify(recorder, never()).recordSuccess(anyLong(), any(), any());
    }

    @Test
    void providerCallFailureKeepsArticlesPendingAndStopsTick() {
        // 제공자 장애: 시도 횟수를 쓰지 않고(기록 없음) 남은 건도 부르지 않음
        fakeSummarizer.failCallFor("First headline");
        fakeSummarizer.respondTo("Second headline", ArticleSummaryOutcome.succeeded(BRIEF));
        givenPendingArticles(createArticle(1L, "First headline"), createArticle(2L, "Second headline"));

        SummaryProcessResult summaryProcessResult = service.processPendingSummaries();

        assertThat(summaryProcessResult).isEqualTo(new SummaryProcessResult(0, 0, false));
        assertThat(fakeSummarizer.getReceivedRequests()).hasSize(1);
        verifyNoInteractions(recorder);
    }

    @Test
    void recordsFailureWhenSuccessRecordThrows() {
        fakeSummarizer.respondTo("First headline", ArticleSummaryOutcome.succeeded(BRIEF));
        givenPendingArticles(createArticle(1L, "First headline"));
        doThrow(new IllegalStateException("db")).when(recorder).recordSuccess(1L, SOURCE_TEXT, BRIEF);

        assertThat(service.processPendingSummaries()).isEqualTo(new SummaryProcessResult(0, 1, false));
        verify(recorder).recordFailure(1L, SOURCE_TEXT);
    }

    @Test
    void countsNeitherDoneNorFailedWhenSourceTextChangedWhileSummarizing() {
        fakeSummarizer.respondTo("First headline", ArticleSummaryOutcome.succeeded(BRIEF));
        givenPendingArticles(createArticle(1L, "First headline"));
        when(recorder.recordSuccess(anyLong(), any(), any())).thenReturn(false);

        assertThat(service.processPendingSummaries()).isEqualTo(new SummaryProcessResult(0, 0, false));
        verify(recorder, never()).recordFailure(anyLong(), any());
    }

    @Test
    void recordsFailureWithoutCallingProviderWhenPendingArticleHasNoSourceText() {
        Article article = createArticle(1L, "First headline");
        ReflectionTestUtils.setField(article.getSummaryState(), "sourceText", null);
        givenPendingArticles(article);

        assertThat(service.processPendingSummaries()).isEqualTo(new SummaryProcessResult(0, 1, false));
        assertThat(fakeSummarizer.getReceivedRequests()).isEmpty();
        verify(recorder).recordFailure(1L, null);
    }

    @Test
    void readsAtMostBatchSize() {
        givenPendingArticles();

        service.processPendingSummaries();

        verify(articleRepository).findSummaryTargets(Limit.of(BATCH_SIZE));
        assertThat(fakeSummarizer.getReceivedRequests()).isEmpty();
    }

    @Test
    void stopsCallingProviderAfterDailyLimitUntilNextSeoulDay() {
        ArticleSummaryProcessService limitedService = createService(3);
        fakeSummarizer.respondTo("A", ArticleSummaryOutcome.succeeded(BRIEF));
        fakeSummarizer.respondTo("B", ArticleSummaryOutcome.succeeded(BRIEF));
        when(recorder.recordSuccess(anyLong(), any(), any())).thenReturn(true);
        givenPendingArticles(createArticle(1L, "A"), createArticle(2L, "B"));

        // 1틱: 2건 호출 (남은 상한 3) > 2틱: 남은 상한 1건만 읽음 > 3틱: 상한 도달로 조회·호출 없음
        limitedService.processPendingSummaries();
        givenPendingArticles(createArticle(3L, "A"));
        limitedService.processPendingSummaries();
        limitedService.processPendingSummaries();

        verify(articleRepository).findSummaryTargets(Limit.of(3));
        verify(articleRepository).findSummaryTargets(Limit.of(1));
        verify(articleRepository, times(2)).findSummaryTargets(any());
        assertThat(fakeSummarizer.getReceivedRequests()).hasSize(3);

        // 서울 날짜가 바뀌면 다시 셈
        clock.advance(Duration.ofDays(1));
        limitedService.processPendingSummaries();
        verify(articleRepository, times(2)).findSummaryTargets(Limit.of(3));
    }

    @Test
    void leavesRemainingArticlesForNextTickWhenTickBudgetIsSpent() {
        fakeSummarizer.respondTo("A", ArticleSummaryOutcome.succeeded(BRIEF));
        when(recorder.recordSuccess(anyLong(), any(), any())).thenAnswer(invocation -> {
            // 첫 건 처리에 틱 시간 상한을 다 씀
            clock.advance(Duration.ofSeconds(TICK_BUDGET_SECONDS + 1));
            return true;
        });
        givenPendingArticles(createArticle(1L, "A"), createArticle(2L, "A"));

        assertThat(service.processPendingSummaries()).isEqualTo(new SummaryProcessResult(1, 0, false));
        assertThat(fakeSummarizer.getReceivedRequests()).hasSize(1);
    }

    @Test
    void skipsWithoutReadingQueueWhenDailyBudgetIsExhausted() {
        when(aiDailyBudgetGuard.isBudgetExhausted()).thenReturn(true);

        assertThat(service.processPendingSummaries()).isEqualTo(new SummaryProcessResult(0, 0, true));
        verifyNoInteractions(articleRepository);
        assertThat(fakeSummarizer.getReceivedRequests()).isEmpty();
    }

    @Test
    void readsBatchSizeAndDailyLimitFromConfigStoreOnEveryTick() {
        // 운영 중 설정을 바꾸면 다음 틱부터 반영 (서버를 다시 띄우지 않음)
        givenPendingArticles();
        service.processPendingSummaries();
        when(systemConfigService.getInt(ConfigKey.SUMMARY_BATCH_SIZE)).thenReturn(4);
        service.processPendingSummaries();

        verify(articleRepository).findSummaryTargets(Limit.of(BATCH_SIZE));
        verify(articleRepository).findSummaryTargets(Limit.of(4));
    }

    // 설정 저장소는 정해 둔 값을 돌려주는 가짜, 하루 예산은 남아 있는 상태
    private ArticleSummaryProcessService createService(int dailyLimit) {
        when(systemConfigService.getInt(ConfigKey.SUMMARY_BATCH_SIZE)).thenReturn(BATCH_SIZE);
        when(systemConfigService.getInt(ConfigKey.SUMMARY_DAILY_LIMIT)).thenReturn(dailyLimit);
        return new ArticleSummaryProcessService(fakeSummarizer, articleRepository, recorder, clock,
                systemConfigService, aiDailyBudgetGuard, TICK_BUDGET_SECONDS);
    }

    private void givenPendingArticles(Article... articles) {
        when(articleRepository.findSummaryTargets(any())).thenReturn(List.of(articles));
    }

    private static Article createArticle(Long id, String title) {
        Article article = Article.create(
                createSource(SourceCode.THE_HACKER_NEWS, ArticleKind.NEWS, ArticleRegion.OVERSEAS, FeedFormat.RSS, null),
                new ArticleDraft(ArticleKind.NEWS, ArticleRegion.OVERSEAS, title, "https://thehackernews.com/" + id,
                        "The Hacker News", NOW, false, null, null, null, false, false, null, SOURCE_TEXT, List.of()),
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
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
