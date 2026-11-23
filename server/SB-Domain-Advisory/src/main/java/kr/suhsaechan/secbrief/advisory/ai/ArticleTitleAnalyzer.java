package kr.suhsaechan.secbrief.advisory.ai;

/**
 * 기사 제목 AI 분석 규약: 제목 > 주제 1개 + 한국어 제목 (실패는 CustomException(AI_API_ERROR))
 */
public interface ArticleTitleAnalyzer {

    /** 분석 가능 여부 (API 키가 없으면 false: 호출하지 않고 건너뜀) */
    boolean isAvailable();

    ArticleTitleAnalysis analyze(ArticleTitleAnalysisRequest request);
}
