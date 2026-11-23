package kr.suhsaechan.secbrief.application.collect;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.collector.ArticleCollector;
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
        when(rssCollector.collect(any(), any())).thenReturn(List.of(createCollected("첫 공지", 1), createCollected("둘째 공지", 2)));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED, SaveOutcome.DUPLICATE);

        assertThat(service.collectSource(kisaSource)).isEqualTo(new CollectRunResult(CollectStatus.SUCCESS, 1, null));
        verify(collectRunRecorder).finishSuccess(eq(RUN_ID), eq(kisaSource.getId()), eq(NOW), eq(1));
    }

    @Test
    void recordsFailedRunAndKeepsLastCollectedAt() {
        when(rssCollector.collect(any(), any())).thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));

        CollectRunResult result = service.collectSource(kisaSource);

        assertThat(result.status()).isEqualTo(CollectStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("EXTERNAL_API_ERROR");
        verify(collectRunRecorder).finishFailed(eq(RUN_ID), eq(kisaSource.getId()), eq(NOW), eq("EXTERNAL_API_ERROR"), anyString());
        verify(collectRunRecorder, never()).finishSuccess(anyLong(), anyLong(), any(), anyInt());
    }

    @Test
    void unexpectedExceptionIsRecordedAsExternalApiError() {
        when(rssCollector.collect(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThat(service.collectSource(kisaSource).errorCode()).isEqualTo("EXTERNAL_API_ERROR");
    }

    @Test
    void countsUniqueViolationAsDuplicateAndContinues() {
        when(rssCollector.collect(any(), any())).thenReturn(List.of(createCollected("첫 공지", 1), createCollected("둘째 공지", 2)));
        when(articleSaveService.saveOrMerge(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(SaveOutcome.CREATED);

        assertThat(service.collectSource(kisaSource)).isEqualTo(new CollectRunResult(CollectStatus.SUCCESS, 1, null));
    }

    @Test
    void skipsDraftsThatFailNormalization() {
        when(rssCollector.collect(any(), any())).thenReturn(List.of(createCollected("  ", 1), createCollected("정상 공지", 2)));
        when(articleSaveService.saveOrMerge(any(), any(), any())).thenReturn(SaveOutcome.CREATED);

        service.collectSource(kisaSource);

        verify(articleSaveService, times(1)).saveOrMerge(any(), any(), any());
    }

    @Test
    void picksCollectorByFeedFormat() {
        when(ghsaCollector.collect(any(), any())).thenReturn(List.of());

        service.collectSource(ghsaSource);

        verify(ghsaCollector).collect(eq(ghsaSource), any());
        verify(rssCollector, never()).collect(any(), any());
    }

    @Test
    void collectsOnlyDueSources() {
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()).thenReturn(List.of(kisaSource, dailysecuSource));
        givenLastAttemptStartedAt(kisaSource, NOW.minus(Duration.ofMinutes(10)));
        givenLastAttemptStartedAt(dailysecuSource, NOW.minus(Duration.ofMinutes(31)));
        when(rssCollector.collect(any(), any())).thenReturn(List.of());

        service.collectDueSources();

        verify(rssCollector, never()).collect(eq(kisaSource), any());
        verify(rssCollector).collect(eq(dailysecuSource), any());
    }

    @Test
    void oneSourceFailureDoesNotStopOthers() {
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()).thenReturn(List.of(kisaSource, dailysecuSource));
        when(collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(anyLong())).thenReturn(Optional.empty());
        when(rssCollector.collect(eq(kisaSource), any())).thenThrow(new CustomException(ErrorCode.FEED_PARSE_ERROR, "broken"));
        when(rssCollector.collect(eq(dailysecuSource), any())).thenReturn(List.of());

        service.collectDueSources();

        verify(collectRunRecorder).finishSuccess(eq(RUN_ID), eq(dailysecuSource.getId()), eq(NOW), eq(0));
    }

    @Test
    void recorderFailureOfOneSourceDoesNotStopOthers() {
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()).thenReturn(List.of(kisaSource, dailysecuSource));
        when(collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(anyLong())).thenReturn(Optional.empty());
        when(collectRunRecorder.start(eq(kisaSource.getId()), any())).thenThrow(new IllegalStateException("db down"));
        when(rssCollector.collect(any(), any())).thenReturn(List.of());

        service.collectDueSources();

        verify(rssCollector).collect(eq(dailysecuSource), any());
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
        return new CollectedArticle(title, "https://www.boho.or.kr/kr/bbs/view.do?nttId=" + number, null,
                NOW.minus(Duration.ofHours(1)), null, null, null, false);
    }
}
