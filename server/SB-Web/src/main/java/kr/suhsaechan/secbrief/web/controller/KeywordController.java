package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.dto.TrendingKeywordsResponse;
import kr.suhsaechan.secbrief.advisory.service.KeywordTrendQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 키워드 API (인증 없음): 인기 키워드 순위 (days 기본 7·1~30 벗어나면 INVALID_REQUEST, size 기본 20·최대 50으로 조정)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/keywords")
public class KeywordController {

    private final KeywordTrendQueryService keywordTrendQueryService;

    @GetMapping("/trending")
    public ApiResponse<TrendingKeywordsResponse> getTrendingKeywords(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.of(keywordTrendQueryService.findTrendingKeywords(days, size));
    }
}
