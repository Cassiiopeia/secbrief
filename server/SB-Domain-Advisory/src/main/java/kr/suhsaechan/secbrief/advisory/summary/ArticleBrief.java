package kr.suhsaechan.secbrief.advisory.summary;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI 가 쓴 브리핑 (필드 5개): 한 줄 결론, 무슨 일, 왜 중요(입력에 영향이 없으면 null), 영향 대상 0~4개, 대응 0~4개
 * 필드마다 역할 하나, 입력에 근거가 없는 필드는 비움 (검증: ArticleSummaryPolicy)
 */
public record ArticleBrief(String oneLine, String whatHappened, String whyItMatters, List<AffectedTarget> affected,
                           List<String> actions) {

    // 이전 구조로 옮길 때 문장을 잇는 글자
    private static final String LEGACY_SENTENCE_SEPARATOR = " ";

    public ArticleBrief {
        affected = affected == null ? List.of() : List.copyOf(affected);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /**
     * 브리핑 > 이전 구조 (이전 앱 빌드·이전 서버 버전이 읽는 값의 단일 변환)
     * 요약 = 한 줄 결론 + 무슨 일 + 왜 중요 (무슨 일은 한 줄 결론을 되풀이하지 않고 이어 쓰는 글이라 결론을 빼면 요지가 빠짐),
     * 핵심 정리 = 영향 대상 줄, 대응 안내 = 대응을 이은 글 (없으면 null)
     */
    public ArticleSummary toLegacySummary() {
        String summary = Stream.of(oneLine, whatHappened, whyItMatters)
                .filter(sentence -> sentence != null && !sentence.isBlank())
                .collect(Collectors.joining(LEGACY_SENTENCE_SEPARATOR));
        List<String> keyPoints = affected.stream().map(AffectedTarget::toLegacyKeyPoint).toList();
        String actionGuide = actions.isEmpty() ? null : String.join(LEGACY_SENTENCE_SEPARATOR, actions);
        return new ArticleSummary(summary, keyPoints, actionGuide);
    }
}
