package kr.suhsaechan.secbrief.application.collect;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.collector.ArticleCollector;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 수집 유스케이스: 주기가 된 출처마다 수집 > 정규화 > 저장·병합 > 수집 기록
 * 출처별로 격리 (한 출처 실패가 다른 출처를 막지 않음)
 */
@Slf4j
@Service
public class ArticleCollectService {

    private final Map<FeedFormat, ArticleCollector> collectorByFeedFormat;
    private final SourceRepository sourceRepository;
    private final CollectRunRepository collectRunRepository;
    private final ArticleSaveService articleSaveService;
    private final CollectRunRecorder collectRunRecorder;
    private final Clock clock;

    public ArticleCollectService(List<ArticleCollector> collectors, SourceRepository sourceRepository,
                                 CollectRunRepository collectRunRepository, ArticleSaveService articleSaveService,
                                 CollectRunRecorder collectRunRecorder, Clock clock) {
        this.collectorByFeedFormat = collectors.stream()
                .collect(Collectors.toMap(ArticleCollector::supportedFeedFormat, Function.identity()));
        this.sourceRepository = sourceRepository;
        this.collectRunRepository = collectRunRepository;
        this.articleSaveService = articleSaveService;
        this.collectRunRecorder = collectRunRecorder;
        this.clock = clock;
    }

    /**
     * 켜진 출처 중 주기가 된 것만 수집 (주기 판정은 마지막 시도 시작 시각 기준)
     */
    public void collectDueSources() {
        for (Source source : sourceRepository.findAllByIsEnabledTrueOrderByIdAsc()) {
            Instant lastAttemptStartedAt = collectRunRepository.findTopBySourceIdOrderByStartedAtDesc(source.getId())
                    .map(CollectRun::getStartedAt)
                    .orElse(null);
            if (!CollectSchedulePolicy.isCollectDue(lastAttemptStartedAt, source.getIntervalMinutes(), clock.instant())) {
                continue;
            }
            try {
                collectSource(source);
            } catch (RuntimeException exception) {
                // 기록 단계 자체가 실패한 경우 (DB 오류 등): 다음 출처로
                log.error("[article collect] failed 500 : source={}, code={}", source.getCode(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    /**
     * 출처 1곳 수집: 시작 기록 > 수집기 선택 > 수집 > 건별 정규화·저장 > 성공 기록 / 예외 시 실패 기록
     */
    public CollectRunResult collectSource(Source source) {
        Instant startedAt = clock.instant();
        Long runId = collectRunRecorder.start(source.getId(), startedAt);
        try {
            ArticleCollector collector = findCollector(source.getFeedFormat());
            List<CollectedArticle> collectedArticles =
                    collector.collect(source, CollectSchedulePolicy.toCollectSince(source, startedAt));
            SaveCounts saveCounts = saveAll(source, collectedArticles, startedAt);

            collectRunRecorder.finishSuccess(runId, source.getId(), clock.instant(), saveCounts.createdCount());
            log.info("[article collect] collected {} : new={}, merged={}",
                    source.getCode(), saveCounts.createdCount(), saveCounts.mergedCount());
            return new CollectRunResult(CollectStatus.SUCCESS, saveCounts.createdCount(), null);
        } catch (RuntimeException exception) {
            return recordFailure(runId, source, exception);
        }
    }

    private ArticleCollector findCollector(FeedFormat feedFormat) {
        return Optional.ofNullable(collectorByFeedFormat.get(feedFormat))
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "no collector for " + feedFormat));
    }

    // 건별 정규화 > 저장 (정규화 실패는 버림, 유일 제약 위반은 중복으로 세고 계속)
    private SaveCounts saveAll(Source source, List<CollectedArticle> collectedArticles, Instant collectedAt) {
        int createdCount = 0;
        int mergedCount = 0;
        for (CollectedArticle collectedArticle : collectedArticles) {
            Optional<ArticleDraft> draft = ArticleNormalizer.normalize(collectedArticle, source, collectedAt);
            if (draft.isEmpty()) {
                continue;
            }
            SaveOutcome outcome = saveOrCountAsDuplicate(source, draft.get(), collectedAt);
            if (outcome == SaveOutcome.CREATED) {
                createdCount++;
            } else if (outcome == SaveOutcome.MERGED) {
                mergedCount++;
            }
        }
        return new SaveCounts(createdCount, mergedCount);
    }

    private SaveOutcome saveOrCountAsDuplicate(Source source, ArticleDraft draft, Instant collectedAt) {
        try {
            return articleSaveService.saveOrMerge(source, draft, collectedAt);
        } catch (DataIntegrityViolationException exception) {
            // 같은 링크·CVE 가 동시에 들어온 경우 (확인과 저장 사이 경합)
            return SaveOutcome.DUPLICATE;
        }
    }

    // 실패 기록: CustomException 이면 그 코드, 아니면 EXTERNAL_API_ERROR (출처 마지막 수집 시각 유지)
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

    private record SaveCounts(int createdCount, int mergedCount) {
    }
}
