package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchResponse;
import kr.suhsaechan.secbrief.advisory.search.ArticleSearchService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기사 검색 API (인증 없음): query 필수(trim 후 2~100자), size 기본 30·1~50, 벗어나면 INVALID_REQUEST
 * 응답의 searchMode 가 실제로 쓴 방식 (SEMANTIC 의미 검색, KEYWORD 글자 일치)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/articles")
public class ArticleSearchController {

    private final ArticleSearchService articleSearchService;

    @GetMapping("/search")
    public ApiResponse<ArticleSearchResponse> searchArticles(
            @RequestParam String query,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.of(articleSearchService.search(query, size));
    }
}
