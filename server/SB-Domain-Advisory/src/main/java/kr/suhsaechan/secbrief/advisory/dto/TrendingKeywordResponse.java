package kr.suhsaechan.secbrief.advisory.dto;

/**
 * 인기 키워드 1개: 기간 안 기사 건수 <-> 바로 앞 같은 길이 기간의 건수, 순위(1부터)
 * rankChange: 앞 기간 순위 - 이번 순위 (양수 상승, 0 유지, 음수 하락, null 앞 기간에 없던 새 키워드)
 */
public record TrendingKeywordResponse(String keyword, long articleCount, long previousArticleCount,
                                      int rank, Integer rankChange) {
}
