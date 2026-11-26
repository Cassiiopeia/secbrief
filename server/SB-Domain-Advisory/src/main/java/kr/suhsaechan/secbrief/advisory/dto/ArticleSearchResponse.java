package kr.suhsaechan.secbrief.advisory.dto;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleSearchMode;

/**
 * 검색 결과: 기사 목록 + 실제로 쓴 검색 방식 (의미 검색을 못 쓰면 KEYWORD)
 */
public record ArticleSearchResponse(List<ArticleResponse> items, ArticleSearchMode searchMode) {
}
