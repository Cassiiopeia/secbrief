package kr.suhsaechan.secbrief.advisory.repository;

/**
 * 키워드별 기사 건수 (인기 키워드 집계 결과, 키워드는 저장된 표기 그대로)
 */
public record KeywordCount(String keyword, long count) {
}
