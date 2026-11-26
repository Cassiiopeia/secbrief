package kr.suhsaechan.secbrief.advisory.summary;

import lombok.extern.slf4j.Slf4j;

/**
 * 요약 시도 1회 (운영 서버·로컬 적재 도구 공통): 작성 > 검증 > 걸린 곳이 있으면 고쳐 쓰기 1회 > 검증 > 더 나은 쪽
 * 제공자 호출은 시도마다 최대 2회, 기사마다 최대 6회 (시도 3회: ArticleSummaryState.MAXIMUM_ATTEMPT_COUNT)
 */
@Slf4j
public final class ArticleSummaryHarness {

    // 시도 1회의 제공자 호출 상한: 작성 1 + 고쳐 쓰기 1
    public static final int MAXIMUM_MODEL_CALLS_PER_ATTEMPT = 2;

    private static final String REPAIR_FAILURE_SEPARATOR = " > repair: ";

    private ArticleSummaryHarness() {
    }

    /**
     * remainingModelCallCount: 지금 쓸 수 있는 제공자 호출 수 (1 이면 고쳐 쓰기를 하지 않음, 호출부가 1 이상일 때만 부름)
     * 작성 호출의 예외는 그대로 던짐 (제공자 장애), 고쳐 쓰기 호출의 예외는 첫 초안이 통과했으면 그 초안으로 끝냄
     */
    public static ArticleSummaryAttempt attempt(ArticleSummarizer summarizer, ArticleSummaryRequest request,
                                                int remainingModelCallCount) {
        // 작성
        ArticleSummaryOutcome firstOutcome = summarizer.summarize(request);
        if (!firstOutcome.isSuccessful()) {
            return new ArticleSummaryAttempt(null, firstOutcome.failureReason(), 1, null, null, false);
        }
        ArticleBrief firstDraft = firstOutcome.brief();
        ArticleSummaryValidation firstValidation =
                ArticleSummaryPolicy.validate(request.title(), request.sourceText(), firstDraft);
        if (firstValidation.isClean() || remainingModelCallCount < MAXIMUM_MODEL_CALLS_PER_ATTEMPT) {
            return toAttempt(firstValidation, firstValidation.rejectionReason(), 1, firstDraft, firstValidation, false);
        }

        // 고쳐 쓰기 (걸린 곳: 필수 필드 거부 또는 버린 선택 필드·목록 항목)
        ArticleSummaryOutcome repairOutcome;
        try {
            repairOutcome = summarizer.repair(request, firstDraft, firstValidation.problems());
        } catch (RuntimeException exception) {
            if (!firstValidation.isAccepted()) {
                throw exception;
            }
            log.warn("[article summary] repair call failed, kept first draft : cause={}", exception.getClass().getSimpleName());
            return toAttempt(firstValidation, null, 1, firstDraft, firstValidation, false);
        }
        if (!repairOutcome.isSuccessful()) {
            return toAttempt(firstValidation,
                    firstValidation.rejectionReason() + REPAIR_FAILURE_SEPARATOR + repairOutcome.failureReason(),
                    MAXIMUM_MODEL_CALLS_PER_ATTEMPT, firstDraft, firstValidation, false);
        }
        ArticleSummaryValidation repairValidation =
                ArticleSummaryPolicy.validate(request.title(), request.sourceText(), repairOutcome.brief());
        if (isRepairBetter(firstValidation, repairValidation)) {
            return toAttempt(repairValidation, null, MAXIMUM_MODEL_CALLS_PER_ATTEMPT, firstDraft, firstValidation, true);
        }
        return toAttempt(firstValidation,
                firstValidation.rejectionReason() + REPAIR_FAILURE_SEPARATOR + repairValidation.rejectionReason(),
                MAXIMUM_MODEL_CALLS_PER_ATTEMPT, firstDraft, firstValidation, false);
    }

    // 고쳐 쓴 글을 쓰는 조건: 통과했고, 첫 초안이 거부였거나 걸린 곳이 늘지 않음 (통과한 첫 초안을 더 나쁜 글로 바꾸지 않음)
    private static boolean isRepairBetter(ArticleSummaryValidation firstValidation, ArticleSummaryValidation repairValidation) {
        if (!repairValidation.isAccepted()) {
            return false;
        }
        return !firstValidation.isAccepted() || repairValidation.problems().size() <= firstValidation.problems().size();
    }

    // 고른 검증 결과 > 시도 결과 (통과면 실패 사유 없음)
    private static ArticleSummaryAttempt toAttempt(ArticleSummaryValidation chosenValidation, String failureReason,
                                                   int modelCallCount, ArticleBrief firstDraft,
                                                   ArticleSummaryValidation firstValidation, boolean isRepairedBriefUsed) {
        return new ArticleSummaryAttempt(chosenValidation.acceptedBrief(),
                chosenValidation.isAccepted() ? null : failureReason,
                modelCallCount, firstDraft, firstValidation, isRepairedBriefUsed);
    }
}
