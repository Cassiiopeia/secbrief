package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticlePageResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기사 목록 API: 필터(enum 대문자 이름, 틀리면 TYPE_MISMATCH) + 커서 + 크기 (인증 없음)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/articles")
public class ArticleController {

    private final ArticleQueryService articleQueryService;

    @GetMapping
    public ApiResponse<ArticlePageResponse> getArticles(
            @RequestParam(required = false) ArticleKind kind,
            @RequestParam(required = false) ArticleRegion region,
            @RequestParam(required = false) ArticleTopic topic,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.of(articleQueryService.findArticles(new ArticleSearchCondition(kind, region, topic, cursor, size)));
    }
}
