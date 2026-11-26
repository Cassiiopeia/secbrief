package kr.suhsaechan.secbrief.advisory.summary;

/**
 * 요약 1건의 결과: 성공(brief, 검증 전 브리핑) 또는 실패(failureReason, 로그용 영어 사유)
 */
public record ArticleSummaryOutcome(ArticleBrief brief, String failureReason) {

    public static ArticleSummaryOutcome succeeded(ArticleBrief brief) {
        return new ArticleSummaryOutcome(brief, null);
    }

    public static ArticleSummaryOutcome failed(String failureReason) {
        return new ArticleSummaryOutcome(null, failureReason);
    }

    public boolean isSuccessful() {
        return brief != null;
    }
}
