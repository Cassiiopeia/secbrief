package kr.suhsaechan.secbrief.advisory.constant;

/**
 * AI 처리 상태: 대기 > 완료, 3회 실패 시 실패 (실패해도 원문 제목으로 계속 노출)
 */
public enum AiStatus {
    PENDING,
    DONE,
    FAILED
}
