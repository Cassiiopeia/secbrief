package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticlePageResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.service.ArticleImageQueryService;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기사 API 2개 (인증 없음): 목록 (필터 enum 대문자 이름, 틀리면 TYPE_MISMATCH + 출처 코드·키워드 + 커서 + 크기), 대표 이미지 (저장된 JPEG)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/articles")
public class ArticleController {

    // 기사의 대표 이미지는 저장 뒤 바뀌지 않음: 1년 (31536000초) 동안 다시 묻지 않게 함
    private static final String IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final ArticleQueryService articleQueryService;
    private final ArticleImageQueryService articleImageQueryService;

    @GetMapping
    public ApiResponse<ArticlePageResponse> getArticles(
            @RequestParam(required = false) ArticleKind kind,
            @RequestParam(required = false) ArticleRegion region,
            @RequestParam(required = false) ArticleTopic topic,
            @RequestParam(required = false) ArticleImportance minimumImportance,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.of(articleQueryService.findArticles(
                new ArticleSearchCondition(kind, region, topic, minimumImportance, sourceCode, keyword, cursor, size)));
    }

    /**
     * 대표 이미지 (image/jpeg): 경로는 ArticleResponse.imagePath 가 알려 줌, 없거나 저장 전이면 RESOURCE_NOT_FOUND
     */
    @GetMapping("/{articleId}/image")
    public ResponseEntity<byte[]> getArticleImage(@PathVariable Long articleId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE_CONTROL)
                .body(articleImageQueryService.findStoredImage(articleId));
    }
}
