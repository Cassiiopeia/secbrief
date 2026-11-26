package kr.suhsaechan.secbrief.advisory.ai;

/**
 * 묶음 안 1건의 결과: 성공(analysis) 또는 실패(failureReason, 로그용 영어 사유)
 */
public record ArticleTitleAnalysisOutcome(ArticleTitleAnalysis analysis, String failureReason) {

    public static ArticleTitleAnalysisOutcome succeeded(ArticleTitleAnalysis analysis) {
        return new ArticleTitleAnalysisOutcome(analysis, null);
    }

    public static ArticleTitleAnalysisOutcome failed(String failureReason) {
        return new ArticleTitleAnalysisOutcome(null, failureReason);
    }

    public boolean isSuccessful() {
        return analysis != null;
    }
}
