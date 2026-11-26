package kr.suhsaechan.secbrief.advisory.dto;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;

/**
 * 브리핑 응답: 한 줄 결론, 무슨 일, 왜 중요(없으면 null), 영향 대상(없으면 빈 목록), 대응(없으면 빈 목록)
 */
public record ArticleBriefResponse(
        String oneLine,
        String whatHappened,
        String whyItMatters,
        List<AffectedTargetResponse> affected,
        List<String> actions
) {

    public static ArticleBriefResponse from(ArticleBrief brief) {
        return new ArticleBriefResponse(
                brief.oneLine(),
                brief.whatHappened(),
                brief.whyItMatters(),
                brief.affected().stream().map(AffectedTargetResponse::from).toList(),
                brief.actions());
    }
}
