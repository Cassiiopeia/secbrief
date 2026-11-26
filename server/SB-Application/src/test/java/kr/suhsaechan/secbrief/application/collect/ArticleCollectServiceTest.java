package kr.suhsaechan.secbrief.application.collect;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import kr.suhsaechan.secbrief.advisory.collector.ArticleCollector;
import kr.suhsaechan.secbrief.advisory.collector.CollectOutcome;
import kr.suhsaechan.secbrief.advisory.collector.FeedValidators;
import kr.suhsaechan.secbrief.advisory.collector.CollectedArticle;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.CollectRun;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.advisory.repository.CollectRunRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleSaveService;
import kr.suhsaechan.secbrief.advisory.service.SaveOutcome;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 수집 유스케이스: 저장 건수 기록, 실패 기록, 중복·정규화 실패 처리, 수집기 선택, 주기 판정, 출처별 격리
 * 변경 없음(304)·캐시 검증값 기록, 링크 묶음 중복 확인, 출처 동시 수집·대기 상한
 */
class ArticleCollectServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final Long RUN_ID = 100L;

    private final ArticleCollector rssCollector = mock(ArticleCollector.class);
    private final ArticleCollector ghsaCollector = mock(ArticleCollector.class);
    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final CollectRunRepository collectRunRepository = mock(CollectRunRepository.class);
    private final ArticleSaveService articleSaveService = mock(ArticleSaveService.class);
    private final CollectRunRecorder collectRunRecorder = mock(CollectRunRecorder.class);

    private final Source kisaSource = createSourceWithId(1L, SourceCode.KISA, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS);
    private final Source dailysecuSource = createSourceWithId(2L, SourceCode.DAILYSECU, ArticleKind.NEWS, ArticleRegion.DOMESTIC, FeedFormat.RSS);
    private final Source ghsaSource = createSourceWithId(3L, SourceCode.GHSA, ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, FeedFormat.GHSA_API);

    private ArticleCollectService service;

    @BeforeEach
    void createService() {
        when(rssCollector.supportedFeedFormat()).thenReturn(FeedFormat.RSS);
        when(ghsaCollector.supportedFeedFormat()).thenReturn(FeedFormat.GHSA_API);
        when(collectRunRecorder.start(anyLong(), any())).thenReturn(RUN_ID);
        service = new ArticleCollectService(List.of(rssCollector, ghsaCollector), sourceRepository, collectRunRepository,
                articleSaveService, collectRunRecorder, Clock.fixed(NOW, SeoulCalendar.SEOUL));
    }

    @Test
    void savesNormalizedArticlesAndRecordsSuccessWithNewCount() {
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of(createCollected("첫 공지", 1), createCollected("둘째 공지", 2))));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED, SaveOutcome.DUPLICATE);

        assertThat(service.collectSource(kisaSource)).isEqualTo(new CollectRunResult(CollectStatus.SUCCESS, 1, null));
        verify(collectRunRecorder).finishSuccess(RUN_ID, kisaSource.getId(), NOW, 1, FeedValidators.NONE);
    }

    @Test
    void skipsRssArticlesPublishedBeforeStoreRange() {
        // 과거 글을 한꺼번에 주는 피드: 30일보다 오래된 게시분은 저장하지 않음, 게시일 없는 항목(수집 시각으로 대체)은 저장
        CollectedArticle oldArticle = new CollectedArticle("오래된 권고", toArticleUrl(1), null,
                NOW.minus(Duration.ofDays(31)), null, null, null, false);
        CollectedArticle boundaryArticle = new CollectedArticle("경계 권고", toArticleUrl(2), null,
                NOW.minus(Duration.ofDays(30)), null, null, null, false);
        CollectedArticle undatedArticle = new CollectedArticle("게시일 없는 권고", toArticleUrl(3), null, null, null, null, null, false);
        when(rssCollector.collect(any(), any()))
                .thenReturn(CollectOutcome.ofArticles(List.of(oldArticle, boundaryArticle, undatedArticle)));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED);

        assertThat(service.collectSource(dailysecuSource).newCount()).isEqualTo(2);
        verify(articleSaveService, never()).saveOrMerge(any(), argThat(draft -> draft.title().equals("오래된 권고")), any());
    }

    @Test
    void keepsOldPublishedArticlesOfKisaSources() {
        // KISA 보호나라는 저장 범위 제한 없음 (경보단계·보고서는 드물게 올라와 30일로 자르면 현재 경보단계가 빠짐)
        CollectedArticle oldAlertLevelNotice = new CollectedArticle("사이버위기 경보 단계 조정", toArticleUrl(1), null,
                NOW.minus(Duration.ofDays(60)), null, null, null, false);
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of(oldAlertLevelNotice)));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED);

        assertThat(service.collectSource(kisaSource).newCount()).isEqualTo(1);
    }

    @Test
    void keepsOldPublishedArticlesOfApiSources() {
        // GHSA·NVD·KEV 는 수집기가 since 로 거르므로 저장 범위 제한을 받지 않음
        CollectedArticle oldAdvisory = new CollectedArticle("Example package: remote code execution",
                "https://github.com/advisories/GHSA-aaaa-bbbb-cccc", "GitHub Advisory",
                NOW.minus(Duration.ofDays(60)), "CVE-2026-5555", null, null, false);
        when(ghsaCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of(oldAdvisory)));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED);

        assertThat(service.collectSource(ghsaSource).newCount()).isEqualTo(1);
    }

    @Test
    void recordsFailedRunAndKeepsLastCollectedAt() {
        when(rssCollector.collect(any(), any())).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));

        CollectRunResult result = service.collectSource(kisaSource);

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("EXTERNAL_API_ERROR");
        verify(collectRunRecorder).finishFailed(eq(RUN_ID), eq(kisaSource.getId()), eq(NOW), eq("EXTERNAL_API_ERROR"), anyString());
        verify(collectRunRecorder, never()).finishSuccess(anyLong(), anyLong(), any(), anyInt(), any());
    }

    @Test
    void unexpectedExceptionIsRecordedAsExternalApiError() {
        when(rssCollector.collect(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThat(service.collectSource(kisaSource).errorCode()).isEqualTo("EXTERNAL_API_ERROR");
    }

    @Test
    void countsUniqueViolationAsDuplicateAndContinues() {
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of(createCollected("첫 공지", 1), createCollected("둘째 공지", 2))));
        when(articleSaveService.saveOrMerge(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(SaveOutcome.CREATED);

        assertThat(service.collectSource(kisaSource)).isEqualTo(new CollectRunResult(CollectStatus.SUCCESS, 1, null));
    }

    @Test
    void skipsDraftsThatFailNormalization() {
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of(createCollected("  ", 1), createCollected("정상 공지", 2))));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED);

        service.collectSource(kisaSource);

        verify(articleSaveService, times(1)).saveOrMerge(any(), any(), any());
    }

    @Test
    void picksCollectorByFeedFormat() {
        when(ghsaCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of()));

        service.collectSource(ghsaSource);

        verify(ghsaCollector).collect(eq(ghsaSource), any());
        verify(rssCollector, never()).collect(any(), any());
    }

    @Test
    void collectsOnlyDueSources() {
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()).thenReturn(List.of(kisaSource, dailysecuSource));
        givenLastAttemptStartedAt(kisaSource, NOW.minus(Duration.ofMinutes(10)));
        givenLastAttemptStartedAt(dailysecuSource, NOW.minus(Duration.ofMinutes(31)));
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of()));

        service.collectDueSources();

        verify(rssCollector, never()).collect(eq(kisaSource), any());
        verify(rssCollector).collect(eq(dailysecuSource), any());
    }

    @Test
    void oneSourceFailureDoesNotStopOthers() {
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()).thenReturn(List.of(kisaSource, dailysecuSource));
        when(collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(anyLong())).thenReturn(Optional.empty());
        when(rssCollector.collect(eq(kisaSource), any())).thenThrow(new CustomException(ErrorCode.FEED_PARSE_ERROR, "broken"));
        when(rssCollector.collect(eq(dailysecuSource), any())).thenReturn(CollectOutcome.ofArticles(List.of()));

        service.collectDueSources();

        verify(collectRunRecorder).finishSuccess(RUN_ID, dailysecuSource.getId(), NOW, 0, FeedValidators.NONE);
    }

    @Test
    void recorderFailureOfOneSourceDoesNotStopOthers() {
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()).thenReturn(List.of(kisaSource, dailysecuSource));
        when(collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(anyLong())).thenReturn(Optional.empty());
        when(collectRunRecorder.start(eq(kisaSource.getId()), any())).thenThrow(new IllegalStateException("db down"));
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of()));

        service.collectDueSources();

        verify(rssCollector).collect(eq(dailysecuSource), any());
    }

    @Test
    void recordsNotModifiedAsSuccessWithoutSaving() {
        FeedValidators storedValidators = new FeedValidators("\"v1\"", null);
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.notModified(storedValidators));

        assertThat(service.collectSource(kisaSource)).isEqualTo(new CollectRunResult(CollectStatus.SUCCESS, 0, null));
        verify(collectRunRecorder).finishSuccess(RUN_ID, kisaSource.getId(), NOW, 0, storedValidators);
        verify(articleSaveService, never()).findSettledOriginalUrls(anyCollection());
        verify(articleSaveService, never()).saveOrMerge(any(), any(), any());
    }

    @Test
    void recordsNewFeedValidatorsWithSuccess() {
        FeedValidators newValidators = new FeedValidators("\"v2\"", "Wed, 25 Nov 2026 00:00:00 GMT");
        when(rssCollector.collect(any(), any()))
                .thenReturn(CollectOutcome.ofArticles(List.of(createCollected("첫 공지", 1)), newValidators));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED);

        service.collectSource(kisaSource);

        verify(collectRunRecorder).finishSuccess(RUN_ID, kisaSource.getId(), NOW, 1, newValidators);
    }

    @Test
    void doesNotRecordFeedValidatorsWhenSavingFails() {
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(
                List.of(createCollected("첫 공지", 1)), new FeedValidators("\"v2\"", null)));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThat(service.collectSource(kisaSource).status()).isEqualTo(CollectStatus.FAILED);
        verify(collectRunRecorder, never()).finishSuccess(anyLong(), anyLong(), any(), anyInt(), any());
    }

    @Test
    void skipsSaveForAlreadyStoredUrlsFoundByOneLookup() {
        when(rssCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of(
                createCollected("이미 있는 공지", 1), createCollected("새 공지", 2), createCollected("이미 있는 공지 둘", 3))));
        when(articleSaveService.findSettledOriginalUrls(anyCollection()))
                .thenReturn(Set.of(toArticleUrl(1), toArticleUrl(3)));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED);

        assertThat(service.collectSource(kisaSource).newCount()).isEqualTo(1);
        verify(articleSaveService, times(1)).findSettledOriginalUrls(List.of(toArticleUrl(1), toArticleUrl(2), toArticleUrl(3)));
        verify(articleSaveService, times(1)).saveOrMerge(any(), any(), any());
    }

    @Test
    void cveDraftsAlwaysGoThroughMergeEvenWhenUrlIsStored() {
        when(ghsaCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of(
                new CollectedArticle("Example package: remote code execution", toArticleUrl(1), "GitHub Advisory",
                        NOW.minus(Duration.ofHours(1)), "CVE-2026-5555", new BigDecimal("9.8"), "3.1", false),
                createCollected("CVE 없는 권고", 2))));
        when(articleSaveService.findSettledOriginalUrls(anyCollection())).thenReturn(Set.of(toArticleUrl(1), toArticleUrl(2)));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.MERGED);

        service.collectSource(ghsaSource);

        // 묶음 조회에는 CVE 없는 링크만, 저장은 CVE 있는 초안만
        verify(articleSaveService).findSettledOriginalUrls(List.of(toArticleUrl(2)));
        verify(articleSaveService, times(1)).saveOrMerge(any(), any(), any());
    }

    @Test
    void skipsUrlLookupWhenAllDraftsHaveCve() {
        when(ghsaCollector.collect(any(), any())).thenReturn(CollectOutcome.ofArticles(List.of(
                new CollectedArticle("Example package: remote code execution", toArticleUrl(1), "GitHub Advisory",
                        NOW.minus(Duration.ofHours(1)), "CVE-2026-5555", new BigDecimal("9.8"), "3.1", false))));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED);

        service.collectSource(ghsaSource);

        verify(articleSaveService, never()).findSettledOriginalUrls(anyCollection());
    }

    @Test
    void collectsDueSourcesConcurrently() {
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()).thenReturn(List.of(kisaSource, ghsaSource));
        when(collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(anyLong())).thenReturn(Optional.empty());
        // 두 수집기가 동시에 도착해야 풀림 (차례로 돌면 첫 수집기가 시간 초과로 실패)
        CyclicBarrier bothCollecting = new CyclicBarrier(2);
        when(rssCollector.collect(any(), any())).thenAnswer(invocation -> awaitBarrier(bothCollecting));
        when(ghsaCollector.collect(any(), any())).thenAnswer(invocation -> awaitBarrier(bothCollecting));

        service.collectDueSources();

        verify(collectRunRecorder).finishSuccess(RUN_ID, kisaSource.getId(), NOW, 0, FeedValidators.NONE);
        verify(collectRunRecorder).finishSuccess(RUN_ID, ghsaSource.getId(), NOW, 0, FeedValidators.NONE);
    }

    @Test
    void returnsAfterWaitLimitAndDoesNotRestartSourceStillCollecting() {
        ArticleCollectService shortWaitService = new ArticleCollectService(List.of(rssCollector, ghsaCollector),
                sourceRepository, collectRunRepository, articleSaveService, collectRunRecorder,
                Clock.fixed(NOW, SeoulCalendar.SEOUL), Duration.ofMillis(50));
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()).thenReturn(List.of(kisaSource));
        when(collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(anyLong())).thenReturn(Optional.empty());
        CountDownLatch releaseCollector = new CountDownLatch(1);
        when(rssCollector.collect(any(), any())).thenAnswer(invocation -> {
            releaseCollector.await(5, TimeUnit.SECONDS);
            return CollectOutcome.ofArticles(List.of());
        });

        // 첫 틱은 상한에서 돌아오고, 둘째 틱은 아직 수집 중인 출처를 다시 시작하지 않음
        shortWaitService.collectDueSources();
        shortWaitService.collectDueSources();
        verify(collectRunRecorder, times(1)).start(eq(kisaSource.getId()), any());

        // 수집이 끝나면 기록됨
        releaseCollector.countDown();
        verify(collectRunRecorder, timeout(5000)).finishSuccess(RUN_ID, kisaSource.getId(), NOW, 0, FeedValidators.NONE);
    }

    private static CollectOutcome awaitBarrier(CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        return CollectOutcome.ofArticles(List.of());
    }

    private void givenLastAttemptStartedAt(Source source, Instant startedAt) {
        when(collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(source.getId()))
                .thenReturn(Optional.of(CollectRun.start(source, startedAt)));
    }

    private static Source createSourceWithId(Long id, String code, ArticleKind kind, ArticleRegion region, FeedFormat feedFormat) {
        Source source = createSource(code, kind, region, feedFormat, null);
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }

    private static CollectedArticle createCollected(String title, int number) {
        return new CollectedArticle(title, toArticleUrl(number), null,
                NOW.minus(Duration.ofHours(1)), null, null, null, false);
    }

    private static String toArticleUrl(int number) {
        return "https://www.boho.or.kr/kr/bbs/view.do?nttId=" + number;
    }
}
