package kr.suhsaechan.secbrief.advisory.support;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.entity.ArticleSummaryState;
import kr.suhsaechan.secbrief.advisory.summary.SummaryKeyPointJson;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 테스트용: 이전 구조의 요약만 있는 행 만들기 (브리핑 열이 생기기 전에 쓴 요약: 브리핑 열은 모두 null)
 * 운영 코드는 이제 브리핑만 쓰므로 필드에 직접 넣음
 */
public final class LegacySummaryFixtures {

    private LegacySummaryFixtures() {
    }

    public static void applyLegacySummary(ArticleSummaryState summaryState, String summary, List<String> keyPoints,
                                          String actionGuide) {
        ReflectionTestUtils.setField(summaryState, "summary", summary);
        ReflectionTestUtils.setField(summaryState, "keyPointsJson", SummaryKeyPointJson.toJson(keyPoints));
        ReflectionTestUtils.setField(summaryState, "actionGuide", actionGuide);
        ReflectionTestUtils.setField(summaryState, "status", SummaryStatus.DONE);
        ReflectionTestUtils.setField(summaryState, "sourceText", null);
    }
}
