package kr.suhsaechan.secbrief.advisory.summary;

/**
 * 요약 시도 1회의 결과: 받아들인 브리핑 또는 실패 사유(로그용 영어 사유), 쓴 제공자 호출 수, 첫 초안과 그 검증 결과
 * firstDraft·firstDraftValidation: 첫 응답을 읽지 못했으면 null (품질 측정용: 첫 통과율, 고쳐 쓰기 효과)
 */
public record ArticleSummaryAttempt(ArticleBrief acceptedBrief, String failureReason, int modelCallCount,
                                    ArticleBrief firstDraft, ArticleSummaryValidation firstDraftValidation,
                                    boolean isRepairedBriefUsed) {

    public boolean isAccepted() {
        return acceptedBrief != null;
    }

    /** 첫 초안이 걸린 곳 없이 통과했는지 */
    public boolean isFirstDraftClean() {
        return firstDraftValidation != null && firstDraftValidation.isClean();
    }

    /** 첫 초안이 검증을 통과했는지 (버린 선택 필드·목록 항목이 있어도 통과) */
    public boolean isFirstDraftAccepted() {
        return firstDraftValidation != null && firstDraftValidation.isAccepted();
    }
}
