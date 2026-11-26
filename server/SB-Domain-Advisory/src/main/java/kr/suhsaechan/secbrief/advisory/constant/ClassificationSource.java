package kr.suhsaechan.secbrief.advisory.constant;

/**
 * 기사 분야를 정한 근거 4개: PRODUCT 제품 사전(CPE·패키지·제목), KEYWORD 분야 낱말, AI 제목 분석이 고른 분야, NONE 못 정함(other)
 */
public enum ClassificationSource {
    PRODUCT,
    KEYWORD,
    AI,
    NONE
}
