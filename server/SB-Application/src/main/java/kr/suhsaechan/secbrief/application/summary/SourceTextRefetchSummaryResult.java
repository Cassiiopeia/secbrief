package kr.suhsaechan.secbrief.application.summary;

/**
 * 다시 요약 틱 1회 결과: 브리핑 반영 건수, 요약 탈락(검증·기록 안 됨) 건수, 입력을 다시 받지 못한 건수, 건너뜀 여부
 */
public record SourceTextRefetchSummaryResult(int doneCount, int rejectedCount, int notRecoveredCount, boolean isSkipped) {

    // 제공자 미설정·예산 소진·하루 상한·회차 끝
    public static final SourceTextRefetchSummaryResult SKIPPED = new SourceTextRefetchSummaryResult(0, 0, 0, true);
}
