package kr.suhsaechan.secbrief.application.collect;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.suhsaechan.secbrief.advisory.collector.KisaBoard;
import kr.suhsaechan.secbrief.advisory.collector.KisaBoardPageWalker;
import kr.suhsaechan.secbrief.advisory.collector.KisaBoardWalkResult;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * KISA 지난 글 1회 채우기: 채우기 전인 KISA 출처 > 게시판 쪽 넘김(보안공지·취약점·보고서 1년, 경보단계 전체) > 주기 수집과 같은 저장 > 끝 표시
 * 이후 새 글은 주기 RSS 수집이 맡음. 출처를 하나씩 차례로 (출처 부담), 실패한 출처는 표시 없이 남겨 다음 틱에 다시
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KisaBoardBackfillService {

    private final SourceRepository sourceRepository;
    private final KisaBoardPageWalker kisaBoardPageWalker;
    private final ArticleCollectService articleCollectService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    // 채우기는 몇 분 걸려 스케줄러 스레드(1개)를 붙잡지 않게 가상 스레드 1개에서 돌림
    private final ExecutorService backfillExecutor =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().name("kisa-backfill-", 0).factory());
    private final AtomicBoolean isBackfillRunning = new AtomicBoolean(false);

    /**
     * 틱 진입점: 채우기가 돌고 있지 않으면 백그라운드로 시작 (바로 반환)
     */
    public void startBackfillIfIdle() {
        if (!isBackfillRunning.compareAndSet(false, true)) {
            return;
        }
        backfillExecutor.execute(() -> {
            try {
                backfillPendingSources();
            } finally {
                isBackfillRunning.set(false);
            }
        });
    }

    /**
     * 채우기 전인 켜진 KISA 출처 전부 차례로 (테스트는 이 메서드를 바로 부름)
     */
    public void backfillPendingSources() {
        for (Source source : findPendingSources()) {
            KisaBoard.fromSourceCode(source.getCode()).ifPresent(board -> backfillIsolated(source, board));
        }
    }

    private List<Source> findPendingSources() {
        return sourceRepository.findAllByIsEnabledTrueOrderByIdAsc().stream()
                .filter(source -> SourceCode.isKisa(source.getCode()) && source.getBackfillCompletedAt() == null)
                .toList();
    }

    // 출처 1곳: 쪽 넘김 > 저장 > 끝 표시 (실패는 로그만, 다른 출처는 계속)
    private void backfillIsolated(Source source, KisaBoard board) {
        Instant startedAt = clock.instant();
        Instant earliestPublishedAt = board.getBackfillWindow().map(startedAt::minus).orElse(null);
        try {
            KisaBoardWalkResult walkResult = kisaBoardPageWalker.collectSince(source, board, earliestPublishedAt);
            ArticleCollectService.SaveCounts saveCounts =
                    articleCollectService.saveCollectedArticles(source, walkResult.articles(), startedAt);
            markCompleted(source.getId(), clock.instant());
            log.info("[kisa backfill] completed {} : pages={}, read={}, new={}, merged={}, pageLimitReached={}",
                    source.getCode(), walkResult.readPageCount(), walkResult.articles().size(),
                    saveCounts.createdCount(), saveCounts.mergedCount(), walkResult.isPageLimitReached());
        } catch (CustomException exception) {
            log.warn("[kisa backfill] failed {} : source={}, code={}", exception.getErrorCode().getStatus().value(),
                    source.getCode(), exception.getErrorCode().name());
        } catch (RuntimeException exception) {
            log.error("[kisa backfill] failed 500 : source={}, code={}", source.getCode(),
                    exception.getClass().getSimpleName());
        }
    }

    // 다시 읽어 표시 (수집 기록이 같은 출처 행을 바꿔도 덮지 않게 짧은 트랜잭션)
    private void markCompleted(Long sourceId, Instant completedAt) {
        transactionTemplate.executeWithoutResult(status -> {
            Optional<Source> source = sourceRepository.findById(sourceId);
            source.ifPresent(storedSource -> storedSource.markBackfillCompleted(completedAt));
        });
    }
}
