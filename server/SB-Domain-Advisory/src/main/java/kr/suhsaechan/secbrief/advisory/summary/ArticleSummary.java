package kr.suhsaechan.secbrief.advisory.summary;

import java.util.List;

/**
 * 이전 구조의 요약 (요약, 핵심 정리, 대응 안내): 이전 앱 빌드·이전 서버 버전이 읽는 값
 * 새 글은 브리핑(ArticleBrief)으로 쓰고 ArticleBrief.toLegacySummary 가 이 모양으로 옮김 (article 의 이전 요약 열 3개)
 */
public record ArticleSummary(String summary, List<String> keyPoints, String actionGuide) {

    public ArticleSummary {
        keyPoints = keyPoints == null ? List.of() : List.copyOf(keyPoints);
    }
}
