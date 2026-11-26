package kr.suhsaechan.secbrief.advisory.ai;

/**
 * AI 제공자 호출 규약 (용도 무관): 지시문 + 사용자 메시지 + JSON 스키마 + 출력 토큰 상한 > 응답 JSON·잘림·거부
 * 예외(CustomException(AI_API_ERROR))는 호출 실패(연결·상태 코드·제한 시간·미설정)뿐, 제목 분석과 요약이 같은 구현을 씀
 */
public interface StructuredCompletionClient {

    /** 제공자 설정값 (secbrief.ai.provider: anthropic, ollama) */
    String getProviderCode();

    /** 호출 가능 여부 (키·주소가 없으면 false: 호출하지 않고 건너뜀) */
    boolean isAvailable();

    /** 제공자 호출 1회 (키·주소·입력 글은 예외 메시지에 넣지 않음) */
    StructuredCompletion requestCompletion(StructuredCompletionRequest request);
}
