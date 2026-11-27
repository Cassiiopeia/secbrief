package kr.suhsaechan.secbrief.tool.summary;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.entity.ArticleSummaryState;
import kr.suhsaechan.secbrief.advisory.policy.SourceTextPolicy;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummarizer;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryAttempt;
import kr.suhsaechan.secbrief.advisory.summary.ArticleSummaryHarness;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 브리핑 만들기 (밀린 요약, 이전 구조 요약의 다시 쓰기): 대상마다 입력 확인 > 요약 시도 최대 3회 (운영과 같은 ArticleSummaryHarness·검증) > 결과 파일에 한 줄
 * 결과 파일에 이미 있는 기사는 건너뜀 (중간에 멈춘 뒤 이어서 돌리기), 제공자 호출 실패는 그 건을 적지 않고 전체를 멈춤
 */
@Slf4j
@RequiredArgsConstructor
public class SummaryBackfillGenerator {

    // 제공자 호출 실패 시 같은 건 재시도 횟수와 대기 (일시 장애·사용량 제한: 넘으면 전체 중단)
    private static final int PROVIDER_RETRY_COUNT = 3;
    private static final long PROVIDER_RETRY_DELAY_MILLISECONDS = 30_000;

    private final ArticleSummarizer articleSummarizer;
    private final int concurrency;

    public void generate(List<BackfillTarget> targets, Path resultsFile) {
        Set<Long> finishedArticleIds = JsonLinesFile.readAll(resultsFile).stream()
                .map(row -> row.path("id").asLong()).collect(Collectors.toSet());
        List<BackfillTarget> remainingTargets =
                targets.stream().filter(target -> !finishedArticleIds.contains(target.id())).toList();
        log.info("[summary backfill] generating : targets={}, alreadyDone={}, concurrency={}",
                targets.size(), finishedArticleIds.size(), concurrency);
        try (JsonLinesFile.Appender resultAppender = JsonLinesFile.openAppender(resultsFile)) {
            ParallelTaskRunner.forEach(remainingTargets, concurrency, target -> {
                BackfillResult articleBackfillResult = generateOne(target);
                resultAppender.append(articleBackfillResult.toJson());
                log.info("[summary backfill] {} : articleId={}, source={}, attempts={}, calls={}, cause={}",
                        articleBackfillResult.status(), articleBackfillResult.id(), articleBackfillResult.sourceCode(),
                        articleBackfillResult.attemptCount(), articleBackfillResult.modelCallCount(),
                        articleBackfillResult.failureReason());
            });
        }
    }

    // 기사 1건: 입력 없음·내용 없음은 제공자를 부르지 않음, 시도마다 작성 + 고쳐 쓰기 (호출 최대 2회)
    BackfillResult generateOne(BackfillTarget target) {
        if (!target.hasSourceText()) {
            return BackfillResult.withoutBrief(target, BackfillResult.Status.NOT_RECOVERABLE, target.recoveryFailureReason());
        }
        if (!SourceTextPolicy.isWorthSummarizing(target.sourceText(), target.title())) {
            return BackfillResult.withoutBrief(target, BackfillResult.Status.INPUT_TOO_THIN, null);
        }
        int modelCallCount = 0;
        String failureReason = null;
        for (int attemptNumber = 1; attemptNumber <= ArticleSummaryState.MAXIMUM_ATTEMPT_COUNT; attemptNumber++) {
            ArticleSummaryAttempt attempt = attemptWithProviderRetry(target);
            modelCallCount += attempt.modelCallCount();
            if (attempt.isAccepted()) {
                return new BackfillResult(target.id(), target.sourceCode(), BackfillResult.Status.ACCEPTED,
                        attempt.acceptedBrief(), attemptNumber, modelCallCount, attempt.isRepairedBriefUsed(), null);
            }
            failureReason = attempt.failureReason();
        }
        return new BackfillResult(target.id(), target.sourceCode(), BackfillResult.Status.REJECTED, null,
                ArticleSummaryState.MAXIMUM_ATTEMPT_COUNT, modelCallCount, false, failureReason);
    }

    private ArticleSummaryAttempt attemptWithProviderRetry(BackfillTarget target) {
        RuntimeException lastFailure = null;
        for (int retryNumber = 0; retryNumber <= PROVIDER_RETRY_COUNT; retryNumber++) {
            try {
                return ArticleSummaryHarness.attempt(articleSummarizer, target.toSummaryRequest(),
                        ArticleSummaryHarness.MAXIMUM_MODEL_CALLS_PER_ATTEMPT);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("[summary backfill] provider call failed : articleId={}, retry={}, cause={}",
                        target.id(), retryNumber, exception.getMessage());
                sleepBeforeRetry();
            }
        }
        throw lastFailure;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(PROVIDER_RETRY_DELAY_MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", exception);
        }
    }
}
