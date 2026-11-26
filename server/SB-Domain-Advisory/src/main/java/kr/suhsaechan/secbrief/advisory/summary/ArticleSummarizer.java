package kr.suhsaechan.secbrief.advisory.summary;

import java.util.List;

/**
 * 기사 AI 요약 규약: 기사 1건(제목 + 요약 입력) > 한국어 브리핑(한 줄 결론·무슨 일·왜 중요·영향 대상·대응) 또는 실패 사유
 * 예외(CustomException(AI_API_ERROR))는 제공자 호출 실패뿐: 호출부가 시도 횟수를 쓰지 않고 다음 틱에 다시 함
 */
public interface ArticleSummarizer {

    /** 제공자 설정값 (secbrief.ai.provider: anthropic, ollama) */
    String getProviderCode();

    /** 요약 가능 여부 (키·주소가 없으면 false: 호출하지 않고 건너뜀) */
    boolean isAvailable();

    /** 거부·잘림·읽을 수 없는 응답은 실패 결과 (예외 아님) */
    ArticleSummaryOutcome summarize(ArticleSummaryRequest request);

    /** 검증에 걸린 초안을 걸린 곳 목록과 함께 다시 쓰게 함 (제공자 호출 1회, 결과·예외 규칙은 summarize 와 같음) */
    ArticleSummaryOutcome repair(ArticleSummaryRequest request, ArticleBrief rejectedDraft, List<SummaryProblem> problems);
}
