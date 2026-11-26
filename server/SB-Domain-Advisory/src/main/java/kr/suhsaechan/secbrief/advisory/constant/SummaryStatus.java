package kr.suhsaechan.secbrief.advisory.constant;

/**
 * AI 요약 상태: 입력 없음 / 대기 > 완료, 3회 실패 시 실패 (완료가 아니면 상세 화면은 제목·사실 값·원문 링크로 열림)
 */
public enum SummaryStatus {
    // 출처가 요약 입력(요약문·공개 설명)을 주지 않음
    NONE,
    PENDING,
    DONE,
    FAILED
}
