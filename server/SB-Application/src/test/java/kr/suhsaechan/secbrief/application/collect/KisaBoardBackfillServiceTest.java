package kr.suhsaechan.secbrief.application.collect;

import static kr.suhsaechan.secbrief.advisory.support.SourceFixtures.createSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import kr.suhsaechan.secbrief.advisory.collector.CollectedArticle;
import kr.suhsaechan.secbrief.advisory.collector.KisaBoard;
import kr.suhsaechan.secbrief.advisory.collector.KisaBoardPageWalker;
import kr.suhsaechan.secbrief.advisory.collector.KisaBoardWalkResult;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * KISA 지난 글 채우기: 채우기 전 KISA 출처만, 범위(보안공지 1년, 경보단계 전체), 주기 수집과 같은 저장, 끝 표시, 실패 출처는 표시 없이 다음 출처 계속
 */
class KisaBoardBackfillServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");

    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final KisaBoardPageWalker kisaBoardPageWalker = mock(KisaBoardPageWalker.class);
    private final ArticleCollectService articleCollectService = mock(ArticleCollectService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final KisaBoardBackfillService service = new KisaBoardBackfillService(sourceRepository, kisaBoardPageWalker,
            articleCollectService, transactionTemplate, Clock.fixed(NOW, SeoulCalendar.SEOUL));

    private final Source noticeSource = createSourceWithId(1L, SourceCode.KISA);
    private final Source alertLevelSource = createSourceWithId(2L, SourceCode.KISA_ALERT_LEVEL);
    private final Source finishedReportSource = createSourceWithId(3L, SourceCode.KISA_REPORT);
    private final Source newsSource = createSourceWithId(4L, SourceCode.DAILYSECU);
    private final List<CollectedArticle> walkedArticles = List.of(new CollectedArticle("공지", KisaBoard.NOTICE.toArticleUrl("1"),
            "KISA", NOW.minus(Duration.ofDays(30)), null, null, null, false));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void runTransactionsInline() {
        finishedReportSource.markBackfillCompleted(NOW.minus(Duration.ofDays(1)));
        when(sourceRepository.findAllByIsEnabledTrueOrderByIdAsc())
                .thenReturn(List.of(noticeSource, alertLevelSource, finishedReportSource, newsSource));
        when(sourceRepository.findById(1L)).thenReturn(Optional.of(noticeSource));
        when(sourceRepository.findById(2L)).thenReturn(Optional.of(alertLevelSource));
        org.mockito.Mockito.doAnswer(invocation -> {
            ((Consumer<TransactionStatus>) invocation.getArgument(0)).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(articleCollectService.saveCollectedArticles(any(), any(), any()))
                .thenReturn(new ArticleCollectService.SaveCounts(1, 0));
    }

    @Test
    void backfillsPendingKisaSourcesWithBoardRangeAndMarksCompleted() {
        when(kisaBoardPageWalker.collectSince(any(), any(), any())).thenReturn(new KisaBoardWalkResult(walkedArticles, 3, false));
        when(kisaBoardPageWalker.collectSince(eq(alertLevelSource), eq(KisaBoard.ALERT_LEVEL), isNull()))
                .thenReturn(new KisaBoardWalkResult(walkedArticles, 7, false));

        service.backfillPendingSources();

        // 보안공지는 1년, 경보단계는 게시판 전체(null), 이미 끝난 보고서·KISA 밖 출처는 건드리지 않음
        verify(kisaBoardPageWalker).collectSince(noticeSource, KisaBoard.NOTICE, NOW.minus(Duration.ofDays(365)));
        verify(kisaBoardPageWalker).collectSince(alertLevelSource, KisaBoard.ALERT_LEVEL, null);
        verify(kisaBoardPageWalker, never()).collectSince(eq(finishedReportSource), any(), any());
        verify(kisaBoardPageWalker, never()).collectSince(eq(newsSource), any(), any());
        verify(articleCollectService).saveCollectedArticles(noticeSource, walkedArticles, NOW);
        assertThat(noticeSource.getBackfillCompletedAt()).isEqualTo(NOW);
        assertThat(alertLevelSource.getBackfillCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void leavesFailedSourceUnmarkedAndContinues() {
        when(kisaBoardPageWalker.collectSince(eq(noticeSource), any(), any()))
                .thenThrow(new CustomException(ErrorCode.EXTERNAL_API_ERROR, "status=503"));
        when(kisaBoardPageWalker.collectSince(eq(alertLevelSource), any(), any()))
                .thenReturn(new KisaBoardWalkResult(walkedArticles, 7, false));

        service.backfillPendingSources();

        assertThat(noticeSource.getBackfillCompletedAt()).isNull();
        assertThat(alertLevelSource.getBackfillCompletedAt()).isEqualTo(NOW);
        verify(articleCollectService, never()).saveCollectedArticles(eq(noticeSource), any(), any());
    }

    private static Source createSourceWithId(Long id, String code) {
        Source source = createSource(code, ArticleKind.ADVISORY, ArticleRegion.DOMESTIC, FeedFormat.RSS, null);
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }
}
