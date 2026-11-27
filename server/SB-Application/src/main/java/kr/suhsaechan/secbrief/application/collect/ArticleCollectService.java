package kr.suhsaechan.secbrief.application.collect;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.collector.ArticleCollector;
import kr.suhsaechan.secbrief.advisory.collector.CollectOutcome;
import kr.suhsaechan.secbrief.advisory.collector.CollectedArticle;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import kr.suhsaechan.secbrief.advisory.constant.FeedFormat;
import kr.suhsaechan.secbrief.advisory.entity.CollectRun;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.policy.ArticleDraft;
import kr.suhsaechan.secbrief.advisory.policy.ArticleNormalizer;
import kr.suhsaechan.secbrief.advisory.policy.CollectSchedulePolicy;
import kr.suhsaechan.secbrief.advisory.repository.CollectRunRepository;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.advisory.service.ArticleSaveService;
import kr.suhsaechan.secbrief.advisory.service.SaveOutcome;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 수집 유스케이스: 주기가 된 출처들을 동시에 수집 > 정규화 > 저장·병합 > 수집 기록
 * 출처별로 격리 (한 출처 실패나 지연이 다른 출처를 막지 않음)
 */
@Slf4j
@Service
public class ArticleCollectService {

    // 한 틱에서 모든 출처 수집을 기다리는 상한 (분): 넘으면 경고만 남기고 틱 종료, 남은 수집은 계속 진행
    private static final long COLLECT_WAIT_LIMIT_MINUTES = 10;

    private final Map<FeedFormat, ArticleCollector> collectorByFeedFormat;
    private final SourceRepository sourceRepository;
    private final CollectRunRepository collectRunRepository;
    private final ArticleSaveService articleSaveService;
    private final CollectRunRecorder collectRunRecorder;
    private final KisaPageSourceTextFiller kisaPageSourceTextFiller;
    private final Clock clock;
    private final Duration collectWaitLimit;
    // 출처마다 가상 스레드 1개 (대부분 외부 응답·NVD 요청 간격 대기라 스레드 풀 크기 제한 불필요)
    private final ExecutorService collectExecutor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("article-collect-", 0).factory());
    // 수집 중인 출처 ID: 대기 상한을 넘겨 아직 도는 출처를 다음 틱이 다시 시작하지 않게 함
    private final Set<Long> collectingSourceIds = ConcurrentHashMap.newKeySet();

    @Autowired
    public ArticleCollectService(List<ArticleCollector> collectors, SourceRepository sourceRepository,
                                 CollectRunRepository collectRunRepository, ArticleSaveService articleSaveService,
                                 CollectRunRecorder collectRunRecorder, KisaPageSourceTextFiller kisaPageSourceTextFiller,
                                 Clock clock) {
        this(collectors, sourceRepository, collectRunRepository, articleSaveService, collectRunRecorder,
                kisaPageSourceTextFiller, clock, Duration.ofMinutes(COLLECT_WAIT_LIMIT_MINUTES));
    }

    // 대기 상한을 줄여 확인하는 테스트용
    ArticleCollectService(List<ArticleCollector> collectors, SourceRepository sourceRepository,
                          CollectRunRepository collectRunRepository, ArticleSaveService articleSaveService,
                          CollectRunRecorder collectRunRecorder, KisaPageSourceTextFiller kisaPageSourceTextFiller,
                          Clock clock, Duration collectWaitLimit) {
        this.collectorByFeedFormat = collectors.stream()
                .collect(Collectors.toMap(ArticleCollector::supportedFeedFormat, Function.identity()));
        this.sourceRepository = sourceRepository;
        this.collectRunRepository = collectRunRepository;
        this.articleSaveService = articleSaveService;
        this.collectRunRecorder = collectRunRecorder;
        this.kisaPageSourceTextFiller = kisaPageSourceTextFiller;
        this.clock = clock;
        this.collectWaitLimit = collectWaitLimit;
    }

    /**
     * 주기가 된 출처 고르기 > 출처별 동시 수집 > 전부 끝날 때까지 대기 (상한 초과 시 경고)
     */
    public void collectDueSources() {
        Map<String, CompletableFuture<Void>> collectFutureBySourceCode = new LinkedHashMap<>();
        for (Source source : findDueSources()) {
            // 이전 틱에서 아직 수집 중이면 건너뜀
            if (!collectingSourceIds.add(source.getId())) {
                continue;
            }
            collectFutureBySourceCode.put(source.getCode(),
                    CompletableFuture.runAsync(() -> collectSourceIsolated(source), collectExecutor));
        }
        awaitAll(collectFutureBySourceCode);
    }

    /**
     * 출처 1곳 수집: 시작 기록 > 수집기 선택 > 수집 > 변경 없으면 바로 성공 기록 > 묶음 저장(KISA 는 게시글 본문을 요약 입력으로) > 성공 기록 / 예외 시 실패 기록
     * 캐시 검증값은 성공 기록에만 실림 (저장 실패 시 이전 값 유지 > 다음 수집이 다시 받음)
     */
    public CollectRunResult collectSource(Source source) {
        Instant startedAt = clock.instant();
        Long runId = collectRunRecorder.start(source.getId(), startedAt);
        try {
            ArticleCollector collector = findCollector(source.getFeedFormat());
            CollectOutcome collectOutcome = collector.collect(source, CollectSchedulePolicy.toCollectSince(source, startedAt));
            if (collectOutcome.isNotModified()) {
                collectRunRecorder.finishSuccess(runId, source.getId(), clock.instant(), 0, collectOutcome.feedValidators());
                log.info("[article collect] not modified {}", source.getCode());
                return new CollectRunResult(CollectStatus.SUCCESS, 0, null);
            }
            SaveCounts saveCounts = saveAll(source, collectOutcome.articles(), startedAt,
                    draft -> kisaPageSourceTextFiller.fillSourceText(source, draft));

            collectRunRecorder.finishSuccess(runId, source.getId(), clock.instant(), saveCounts.createdCount(),
                    collectOutcome.feedValidators());
            log.info("[article collect] collected {} : new={}, merged={}",
                    source.getCode(), saveCounts.createdCount(), saveCounts.mergedCount());
            return new CollectRunResult(CollectStatus.SUCCESS, saveCounts.createdCount(), null);
        } catch (RuntimeException exception) {
            return recordFailure(runId, source, exception);
        }
    }

    /**
     * 수집기 밖에서 모은 원본 항목 저장 (KISA 지난 글 채우기): 주기 수집과 같은 정규화·중복 확인·저장, 수집 기록은 남기지 않음
     * 게시글 페이지는 읽지 않음 (지난 글 수백 건, 밀린 브리핑은 로컬 도구로)
     */
    public SaveCounts saveCollectedArticles(Source source, List<CollectedArticle> collectedArticles, Instant collectedAt) {
        return saveAll(source, collectedArticles, collectedAt, UnaryOperator.identity());
    }

    // 켜진 출처 중 주기가 된 것 (주기 판정은 마지막 시도 시작 시각 기준)
    private List<Source> findDueSources() {
        List<Source> dueSources = new ArrayList<>();
        for (Source source : sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()) {
            Instant lastAttemptStartedAt = collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(source.getId())
                    .map(CollectRun::getStartedAt)
                    .orElse(null);
            if (CollectSchedulePolicy.isCollectDue(lastAttemptStartedAt, source.getIntervalMinutes(), clock.instant())) {
                dueSources.add(source);
            }
        }
        return dueSources;
    }

    // 수집 스레드 1개의 본체: 예외를 밖으로 내지 않음, 수집 중 표시는 항상 해제 (성공·실패 공통)
    private void collectSourceIsolated(Source source) {
        try {
            collectSource(source);
        } catch (RuntimeException exception) {
            // 기록 단계 자체가 실패한 경우 (DB 오류 등)
            log.error("[article collect] failed 500 : source={}, code={}", source.getCode(),
                    exception.getClass().getSimpleName());
        } finally {
            collectingSourceIds.remove(source.getId());
        }
    }

    // 모든 출처 수집 대기: 상한을 넘으면 아직 도는 출처를 경고로 남기고 반환 (수집은 끊지 않음)
    private void awaitAll(Map<String, CompletableFuture<Void>> collectFutureBySourceCode) {
        try {
            CompletableFuture.allOf(collectFutureBySourceCode.values().toArray(CompletableFuture[]::new))
                    .get(collectWaitLimit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            List<String> runningSourceCodes = collectFutureBySourceCode.entrySet().stream()
                    .filter(entry -> !entry.getValue().isDone())
                    .map(Map.Entry::getKey)
                    .toList();
            log.warn("[article collect] wait limit exceeded : limitSeconds={}, running={}",
                    collectWaitLimit.toSeconds(), runningSourceCodes);
        } catch (InterruptedException exception) {
            // 서버 종료 등: 인터럽트 표시를 되살리고 틱 종료
            Thread.currentThread().interrupt();
            log.warn("[article collect] interrupted while waiting sources");
        } catch (ExecutionException exception) {
            // collectSourceIsolated 가 RuntimeException 을 삼키므로 Error 계열만 여기로 옴
            log.error("[article collect] failed 500 : code={}", exception.getCause().getClass().getSimpleName());
        }
    }

    private ArticleCollector findCollector(FeedFormat feedFormat) {
        return Optional.ofNullable(collectorByFeedFormat.get(feedFormat))
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "no collector for " + feedFormat));
    }

    // 전체 정규화 (실패는 버림) > 저장 범위보다 오래된 게시분 제외 (RSS) > 이미 있는 링크 묶음 조회 > 건별 저장 (CVE 없는 기존 링크는 저장을 부르지 않고 중복으로 셈)
    // draftCompleter: 저장 직전 초안 보완 (중복으로 건너뛴 건은 부르지 않음, 트랜잭션 밖)
    private SaveCounts saveAll(Source source, List<CollectedArticle> collectedArticles, Instant collectedAt,
                               UnaryOperator<ArticleDraft> draftCompleter) {
        Instant earliestStoredPublishedAt = CollectSchedulePolicy.toEarliestStoredPublishedAt(source, collectedAt);
        List<ArticleDraft> drafts = collectedArticles.stream()
                .map(collectedArticle -> ArticleNormalizer.normalize(collectedArticle, source, collectedAt))
                .flatMap(Optional::stream)
                .filter(draft -> earliestStoredPublishedAt == null || !draft.publishedAt().isBefore(earliestStoredPublishedAt))
                .toList();
        Set<String> existingOriginalUrls = findSettledOriginalUrlsOfDraftsWithoutCve(drafts);

        int createdCount = 0;
        int mergedCount = 0;
        for (ArticleDraft draft : drafts) {
            if (isKnownDuplicate(draft, existingOriginalUrls)) {
                continue;
            }
            SaveOutcome outcome = saveOrCountAsDuplicate(source, draftCompleter.apply(draft), collectedAt);
            if (outcome == SaveOutcome.CREATED) {
                createdCount++;
            } else if (outcome == SaveOutcome.MERGED) {
                mergedCount++;
            }
        }
        return new SaveCounts(createdCount, mergedCount);
    }

    // CVE 가 있는 초안은 제외: 링크가 이미 있어도 병합 규칙(KEV 표시, NVD 링크 교체, 긴급)을 타야 함
    private Set<String> findSettledOriginalUrlsOfDraftsWithoutCve(List<ArticleDraft> drafts) {
        List<String> originalUrlsWithoutCve = drafts.stream()
                .filter(draft -> draft.cveId() == null)
                .map(ArticleDraft::originalUrl)
                .toList();
        return originalUrlsWithoutCve.isEmpty() ? Set.of() : articleSaveService.findSettledOriginalUrls(originalUrlsWithoutCve);
    }

    private static boolean isKnownDuplicate(ArticleDraft draft, Set<String> existingOriginalUrls) {
        return draft.cveId() == null && existingOriginalUrls.contains(draft.originalUrl());
    }

    private SaveOutcome saveOrCountAsDuplicate(Source source, ArticleDraft draft, Instant collectedAt) {
        try {
            return articleSaveService.saveOrMerge(source, draft, collectedAt);
        } catch (DataIntegrityViolationException exception) {
            // 같은 링크·CVE 가 동시에 들어와 재시도까지 유일 제약에 걸린 경우 (확인과 저장 사이 경합)
            return SaveOutcome.DUPLICATE;
        }
    }

    // 실패 기록: CustomException 이면 그 코드, 아니면 EXTERNAL_API_ERROR (출처 마지막 수집 시각·캐시 검증값 유지)
    private CollectRunResult recordFailure(Long runId, Source source, RuntimeException exception) {
        ErrorCode errorCode = exception instanceof CustomException customException
                ? customException.getErrorCode()
                : ErrorCode.EXTERNAL_API_ERROR;
        collectRunRecorder.finishFailed(runId, source.getId(), clock.instant(), errorCode.name(),
                ArticleNormalizer.toSanitizedErrorMessage(exception.getMessage()));
        log.warn("[article collect] failed {} : source={}, code={}",
                errorCode.getStatus().value(), source.getCode(), errorCode.name());
        return new CollectRunResult(CollectStatus.FAILED, 0, errorCode.name());
    }

    /** 저장 결과 건수: 새 기사, 기존 기사에 합친 것 */
    public record SaveCounts(int createdCount, int mergedCount) {
    }
}
