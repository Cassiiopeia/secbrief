package kr.suhsaechan.secbrief.application.summary;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryOutcome;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryPolicy;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryRequest;
import kr.suhsaechan.secbrief.advisory.summary.SummaryProblem;
import kr.suhsaechan.secbrief.advisory.summary.SummaryWritingKind;
import kr.suhsaechan.secbrief.application.ai.AiDailyBudgetGuard;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 요약 묶음 처리의 하네스 연결: 검증에 걸린 초안은 고쳐 쓰기 1회 (건당 호출 최대 2회, 실패는 시도 1회로 셈)
 * 고쳐 쓰기 호출도 하루 호출 상한에 셈, 글 종류는 출처 코드·주제로 고름, 내용 없는 입력은 호출 없이 실패
 */
class ArticleSummaryRepairProcessTest {

    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final int BATCH_SIZE = 10;
    private static final long TICK_BUDGET_SECONDS = 90;
    private static final int DAILY_LIMIT = 300;
    private static final String SOURCE_TEXT = "Attackers are exploiting a flaw in Example VPN 2.4.0 to steal session tokens.";
    private static final ArticleBrief BRIEF =
            new ArticleBrief("Example VPN 결함으로 세션 토큰이 털리고 있어요.",
                    "공격자가 Example VPN 2.4.0의 결함을 악용하고 있어요.", null, List.of(new AffectedTarget("Example VPN", "2.4.0")),
                    List.of());
    // 입력에 없는 버전(2.4.1)을 쓴 초안: 검증에 걸림
    private static final ArticleBrief INVENTED_NUMBER_BRIEF =
            new ArticleBrief("Example VPN 결함으로 세션 토큰이 털리고 있어요.",
                    "Example VPN 2.4.1 버전으로 올려야 해요.", null, List.of(), List.of());

    private final FakeArticleSummarizer fakeSummarizer = new FakeArticleSummarizer();
    private final ArticleRepository articleRepository = mock(ArticleRepository.class);
    private final ArticleSummaryResultRecorder recorder = mock(ArticleSummaryResultRecorder.class);
    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final ArticleSummaryProcessService service = createService(DAILY_LIMIT);

    @Test
    void repairsRejectedDraftOnceAndRecordsRepairedSummary() {
        fakeSummarizer.respondTo("First headline", ArticleSummaryOutcome.succeeded(INVENTED_NUMBER_BRIEF));
        fakeSummarizer.respondToRepairOf("First headline", ArticleSummaryOutcome.succeeded(BRIEF));
        givenPendingArticles(createArticle(1L, "First headline"));
        when(recorder.recordSuccess(anyLong(), any(), any())).thenReturn(true);

        SummaryProcessResult result = service.processPendingSummaries();

        assertThat(result).isEqualTo(new SummaryProcessResult(1, 0, false));
        // 고쳐 쓰기 요청에는 걸린 곳(입력에 없는 숫자)이 그대로 감
        assertThat(fakeSummarizer.getReceivedRepairProblems()).containsExactly(List.of(
                new SummaryProblem("whatHappened", ArticleSummaryPolicy.REASON_NUMBER_NOT_IN_SOURCE, "2.4.1")));
        verify(recorder).recordSuccess(1L, SOURCE_TEXT, BRIEF);
        verify(recorder, never()).recordFailure(anyLong(), any());
    }

    @Test
    void recordsOneFailureWhenRepairIsRejectedTooAndCallsProviderAtMostTwicePerArticle() {
        fakeSummarizer.respondTo("First headline", ArticleSummaryOutcome.succeeded(INVENTED_NUMBER_BRIEF));
        fakeSummarizer.respondToRepairOf("First headline", ArticleSummaryOutcome.succeeded(INVENTED_NUMBER_BRIEF));
        givenPendingArticles(createArticle(1L, "First headline"));

        SummaryProcessResult result = service.processPendingSummaries();

        // 작성 1 + 고쳐 쓰기 1 = 2회, 시도 횟수는 1만 오름 (recordFailure 1회)
        assertThat(result).isEqualTo(new SummaryProcessResult(0, 1, false));
        assertThat(fakeSummarizer.getReceivedRequests()).hasSize(1);
        assertThat(fakeSummarizer.getReceivedRepairProblems()).hasSize(1);
        verify(recorder, times(1)).recordFailure(1L, SOURCE_TEXT);
    }

    @Test
    void repairCallsCountTowardDailyLimitAndLastCallIsNotSpentOnRepair() {
        // 하루 상한 3: 1건째 작성 + 고쳐 쓰기(2회) > 2건째는 남은 1회로 작성만 (고쳐 쓰기 없음) > 3건째는 부르지 않음
        ArticleSummaryProcessService limitedService = createService(3);
        fakeSummarizer.respondTo("A", ArticleSummaryOutcome.succeeded(INVENTED_NUMBER_BRIEF));
        fakeSummarizer.respondToRepairOf("A", ArticleSummaryOutcome.succeeded(BRIEF));
        when(recorder.recordSuccess(anyLong(), any(), any())).thenReturn(true);
        givenPendingArticles(createArticle(1L, "A"), createArticle(2L, "A"), createArticle(3L, "A"));

        SummaryProcessResult result = limitedService.processPendingSummaries();

        assertThat(result).isEqualTo(new SummaryProcessResult(1, 1, false));
        assertThat(fakeSummarizer.getReceivedRequests()).hasSize(2);
        assertThat(fakeSummarizer.getReceivedRepairProblems()).hasSize(1);
        verify(recorder).recordFailure(2L, SOURCE_TEXT);
    }

    @Test
    void choosesWritingKindFromSourceCodeAndTopic() {
        Article eventArticle = createArticle(1L, "Webinar headline");
        ReflectionTestUtils.setField(eventArticle, "topic", ArticleTopic.EVENT);
        givenPendingArticles(eventArticle, createArticle(2L, "News headline"));

        service.processPendingSummaries();

        assertThat(fakeSummarizer.getReceivedRequests()).extracting(ArticleSummaryRequest::writingKind)
                .containsExactly(SummaryWritingKind.EVENT_NOTICE, SummaryWritingKind.NEWS_ARTICLE);
    }

    @Test
    void recordsFailureWithoutCallingProviderWhenSourceTextIsOnlyWebAddress() {
        // 이 규칙 이전에 저장된 대기 행 (Debian 권고: 추적 페이지 주소뿐)
        String addressOnlySourceText = "https://security-tracker.example.test/tracker/EXSA-0000-1";
        Article article = createArticle(1L, "EXSA-0000-1 example - security update");
        ReflectionTestUtils.setField(article.getSummaryState(), "sourceText", addressOnlySourceText);
        givenPendingArticles(article);

        assertThat(service.processPendingSummaries()).isEqualTo(new SummaryProcessResult(0, 1, false));
        assertThat(fakeSummarizer.getReceivedRequests()).isEmpty();
        verify(recorder).recordFailure(1L, addressOnlySourceText);
    }

    private ArticleSummaryProcessService createService(int dailyLimit) {
        when(systemConfigService.getInt(ConfigKey.SUMMARY_BATCH_SIZE)).thenReturn(BATCH_SIZE);
        when(systemConfigService.getInt(ConfigKey.SUMMARY_DAILY_LIMIT)).thenReturn(dailyLimit);
        return new ArticleSummaryProcessService(fakeSummarizer, articleRepository, recorder,
                Clock.fixed(NOW, ZoneOffset.UTC), systemConfigService, mock(AiDailyBudgetGuard.class), TICK_BUDGET_SECONDS);
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
}
