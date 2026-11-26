package kr.suhsaechan.secbrief.advisory.summary;

import java.util.List;

/**
 * AI 가 쓴 요약 (검증 전): 한국어 요약, 핵심 정리, 대응 안내(입력에 조치가 없으면 null)
 * 저장 전에 ArticleSummaryPolicy 가 원문에 없는 숫자 등을 거름
 */
public record ArticleSummary(String summary, List<String> keyPoints, String actionGuide) {

    public ArticleSummary {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
    }
}
