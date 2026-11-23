package kr.suhsaechan.secbrief.advisory.dto;

import java.util.List;

/**
 * 기사 목록 1페이지: 항목 + 다음 위치 커서 (끝이면 null)
 */
public record ArticlePageResponse(List<ArticleResponse> items, String nextCursor) {
}
