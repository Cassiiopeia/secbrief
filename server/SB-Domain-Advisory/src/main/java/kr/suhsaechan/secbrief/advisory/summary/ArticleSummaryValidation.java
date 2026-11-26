package kr.suhsaechan.secbrief.advisory.summary;

/**
 * 요약 검증 결과: 받아들인 요약(acceptedSummary) 또는 거부 사유(rejectionReason, 로그용 영어 사유)
 */
public record ArticleSummaryValidation(ArticleSummary acceptedSummary, String rejectionReason) {

    public static ArticleSummaryValidation accepted(ArticleSummary acceptedSummary) {
        return new ArticleSummaryValidation(acceptedSummary, null);
    }

    public static ArticleSummaryValidation rejected(String rejectionReason) {
        return new ArticleSummaryValidation(null, rejectionReason);
    }

    public boolean isAccepted() {
        return acceptedSummary != null;
    }
}
