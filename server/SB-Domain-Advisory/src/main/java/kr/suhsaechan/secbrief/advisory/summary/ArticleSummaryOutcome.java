package kr.suhsaechan.secbrief.advisory.summary;

/**
 * 요약 1건의 결과: 성공(summary) 또는 실패(failureReason, 로그용 영어 사유)
 */
public record ArticleSummaryOutcome(ArticleSummary summary, String failureReason) {

    public static ArticleSummaryOutcome succeeded(ArticleSummary summary) {
        return new ArticleSummaryOutcome(summary, null);
    }

    public static ArticleSummaryOutcome failed(String failureReason) {
        return new ArticleSummaryOutcome(null, failureReason);
    }

    public boolean isSuccessful() {
        return summary != null;
    }
}
