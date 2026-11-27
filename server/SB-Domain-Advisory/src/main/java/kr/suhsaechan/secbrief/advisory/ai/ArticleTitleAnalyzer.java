package kr.suhsaechan.secbrief.advisory.ai;

import java.util.List;

/**
 * 기사 제목 AI 분석 규약: 제목 여러 건 > 건별 결과 (주제 1개 + 한국어 제목 또는 실패 사유)
 * 예외(CustomException(AI_API_ERROR))는 제공자 호출 실패뿐: 호출부가 시도 횟수를 쓰지 않고 다음 틱에 다시 함
 */
public interface ArticleTitleAnalyzer {

    /** 제공자 설정값 (secbrief.ai.provider: anthropic, ollama) */
    String getProviderCode();

    /** 분석 가능 여부 (키·주소가 없으면 false: 호출하지 않고 건너뜀) */
    boolean isAvailable();

    /** 결과 목록은 요청과 같은 순서·같은 길이 (응답에 빠진 건은 실패 결과) */
    List<ArticleTitleAnalysisOutcome> analyzeBatch(List<ArticleTitleAnalysisRequest> requests);

    /**
     * 번역 제목 고쳐 쓰기 (거부 사유와 함께 1회): 결과는 요청과 같은 순서·길이, 고치지 못한 건은 null
     * 예외는 analyzeBatch 와 같음 (제공자 호출 실패뿐)
     */
    List<String> repairTranslatedTitles(List<TranslatedTitleRepairRequest> requests);
}
