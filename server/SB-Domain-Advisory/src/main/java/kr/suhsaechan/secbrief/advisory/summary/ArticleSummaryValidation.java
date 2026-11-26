package kr.suhsaechan.secbrief.advisory.summary;

import java.util.List;

/**
 * 브리핑 검증 결과: 받아들인 브리핑(acceptedBrief) 또는 거부 사유(rejectionReason, 로그용 영어 사유) + 걸린 곳 목록
 * problems: 거부면 필수 필드(한 줄 결론, 무슨 일)가 걸린 곳 포함, 통과면 버린 선택 필드·목록 항목만 (비어 있으면 흠 없는 통과)
 */
public record ArticleSummaryValidation(ArticleBrief acceptedBrief, String rejectionReason, List<SummaryProblem> problems) {

    public ArticleSummaryValidation {
        problems = problems == null ? List.of() : List.copyOf(problems);
    }

    public static ArticleSummaryValidation accepted(ArticleBrief acceptedBrief, List<SummaryProblem> problems) {
        return new ArticleSummaryValidation(acceptedBrief, null, problems);
    }

    public static ArticleSummaryValidation rejected(String rejectionReason, List<SummaryProblem> problems) {
        return new ArticleSummaryValidation(null, rejectionReason, problems);
    }

    public boolean isAccepted() {
        return acceptedBrief != null;
    }

    /** 걸린 곳 없이 통과했는지 (고쳐 쓸 필요 없음) */
    public boolean isClean() {
        return isAccepted() && problems.isEmpty();
    }
}
