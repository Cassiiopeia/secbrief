package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.dto.ArticleDetailResponse;
import kr.suhsaechan.secbrief.advisory.service.ArticleDetailQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기사 상세(브리프) API (인증 없음): AI 요약 + 원문에 있는 사실 값 + 관련 기사, 없는 id 는 RESOURCE_NOT_FOUND
 * 목록·이미지는 ArticleController (같은 /api/v1/articles 아래)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/articles")
public class ArticleDetailController {

    private final ArticleDetailQueryService articleDetailQueryService;

    @GetMapping("/{articleId}")
    public ApiResponse<ArticleDetailResponse> getArticleDetail(@PathVariable Long articleId) {
        return ApiResponse.of(articleDetailQueryService.findArticleDetail(articleId));
    }
}
