package kr.suhsaechan.secbrief.web.controller;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticlePageResponse;
import kr.suhsaechan.secbrief.advisory.dto.ArticleSearchCondition;
import kr.suhsaechan.secbrief.advisory.dto.ArticleTagFilter;
import kr.suhsaechan.secbrief.advisory.dto.NewArticleCountsResponse;
import kr.suhsaechan.secbrief.advisory.service.ArticleImageQueryService;
import kr.suhsaechan.secbrief.advisory.service.ArticleQueryService;
import kr.suhsaechan.secbrief.advisory.service.NewArticleCountsQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기사 API 3개 (인증 없음): 목록 (필터 enum 대문자 이름, 틀리면 TYPE_MISMATCH + 출처 코드·키워드·긴급만·분류 조건 + 커서 + 크기), 대표 이미지 (저장된 JPEG), 오늘 새 소식 건수
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/articles")
public class ArticleController {

    // 기사의 대표 이미지는 저장 뒤 바뀌지 않음: 1년 (31536000초) 동안 다시 묻지 않게 함
    private static final String IMAGE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final ArticleQueryService articleQueryService;
    private final ArticleImageQueryService articleImageQueryService;
    private final NewArticleCountsQueryService newArticleCountsQueryService;

    @GetMapping
    public ApiResponse<ArticlePageResponse> getArticles(
            @RequestParam(required = false) ArticleKind kind,
            @RequestParam(required = false) ArticleRegion region,
            @RequestParam(required = false) ArticleTopic topic,
            @RequestParam(required = false) ArticleImportance minimumImportance,
            @RequestParam(required = false) String sourceCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isUrgent,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            // 분류 조건 (#93): 쉼표 목록, 주요 여부, 수집 시각 이후 (ISO)
            @RequestParam(required = false) List<String> sourceCodes,
            @RequestParam(required = false) String fieldCode,
            @RequestParam(required = false) String ecosystemCode,
            @RequestParam(required = false) Boolean isNotable,
            @RequestParam(required = false) List<String> interestProductCodes,
            @RequestParam(required = false) List<String> interestFieldCodes,
            @RequestParam(required = false) List<String> interestEcosystemCodes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant collectedSince) {
        ArticleTagFilter tagFilter = new ArticleTagFilter(sourceCodes, fieldCode, ecosystemCode, isNotable,
                interestProductCodes, interestFieldCodes, interestEcosystemCodes, collectedSince);
        return ApiResponse.of(articleQueryService.findArticles(new ArticleSearchCondition(kind, region, topic,
                minimumImportance, sourceCode, keyword, isUrgent, cursor, size, tagFilter)));
    }

    /**
     * 오늘 새로 들어온 소식 건수 (홈 띠·취약점 칩 +N): 기기 날짜 0시 이후 수집분, timeZone 없으면 Asia/Seoul
     */
    @GetMapping("/new-counts")
    public ApiResponse<NewArticleCountsResponse> getNewArticleCounts(@RequestParam(required = false) String timeZone) {
        return ApiResponse.of(newArticleCountsQueryService.findTodayCounts(timeZone));
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
