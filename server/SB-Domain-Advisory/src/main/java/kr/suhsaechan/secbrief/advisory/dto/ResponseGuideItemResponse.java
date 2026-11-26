package kr.suhsaechan.secbrief.advisory.dto;

import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideMatchReason;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.fact.SourceFact;
import kr.suhsaechan.secbrief.advisory.fact.SourceFactLabel;

/**
 * 대응 모아보기 1건: 기사(목록과 같은 모양, 원문 링크·한 줄 결론 포함) + 출처 표시 이름 + 묶인 이유 + KEV 조치 기한(원문 값만)
 * 기사 본문은 싣지 않음 (언론 기사는 제목·링크·직접 쓴 한 줄 결론만)
 */
public record ResponseGuideItemResponse(
        ArticleResponse article,
        // 출처 표시 이름 (KISA 보호나라, Red Hat 보안 권고 ...)
        String sourceName,
        ResponseGuideMatchReason matchReason,
        // 같은 CVE 로 묶였을 때 겹친 CVE ID (그 밖에는 null)
        String matchedCveId,
        // KEV 조치 기한: 원문 문자열 그대로 (없으면 null)
        String actionDueDate
) {

    public static ResponseGuideItemResponse of(Article article, ResponseGuideMatchReason matchReason, String matchedCveId) {
        return new ResponseGuideItemResponse(
                ArticleResponse.from(article),
                article.getSource().getDisplayName(),
                matchReason,
                matchedCveId,
                findActionDueDate(article));
    }

    private static String findActionDueDate(Article article) {
        return article.getSourceFacts().stream()
                .filter(sourceFact -> SourceFactLabel.ACTION_DUE_DATE.equals(sourceFact.label()))
                .map(SourceFact::value)
                .filter(value -> !SourceFactLabel.ABSENT_VALUE.equals(value))
                .findFirst()
                .orElse(null);
    }
}
