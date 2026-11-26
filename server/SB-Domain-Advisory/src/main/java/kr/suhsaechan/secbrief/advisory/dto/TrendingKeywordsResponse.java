package kr.suhsaechan.secbrief.advisory.dto;

import java.util.List;

/**
 * 인기 키워드 순위 (기사 건수 많은 순)
 */
public record TrendingKeywordsResponse(List<TrendingKeywordResponse> keywords) {
}
