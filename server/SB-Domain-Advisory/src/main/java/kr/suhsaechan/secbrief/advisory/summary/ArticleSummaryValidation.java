package kr.suhsaechan.secbrief.advisory.summary;

import java.util.List;

/**
 * 요약 검증 결과: 받아들인 요약(acceptedSummary) 또는 거부 사유(rejectionReason, 로그용 영어 사유) + 걸린 곳 목록
 * problems: 거부면 요약이 걸린 곳 포함, 통과면 버린 핵심 정리·대응 안내만 (비어 있으면 흠 없는 통과)
 */
public record ArticleSummaryValidation(ArticleSummary acceptedSummary, String rejectionReason, List<SummaryProblem> problems) {

    public ArticleSummaryValidation {
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public static ArticleSummaryValidation accepted(ArticleSummary acceptedSummary, List<SummaryProblem> problems) {
        return new ArticleSummaryValidation(acceptedSummary, null, problems);
    }

    public static ArticleSummaryValidation rejected(String rejectionReason, List<SummaryProblem> problems) {
        return new ArticleSummaryValidation(null, rejectionReason, problems);
    }

    public boolean isAccepted() {
        return acceptedSummary != null;
    }

    /** 걸린 곳 없이 통과했는지 (고쳐 쓸 필요 없음) */
    public boolean isClean() {
        return isAccepted() && problems.isEmpty();
    }
}
