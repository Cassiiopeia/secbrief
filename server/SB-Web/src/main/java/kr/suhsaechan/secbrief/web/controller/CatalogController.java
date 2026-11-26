package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.dto.CatalogTagsResponse;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분류 태그 API (인증 없음): 기술 분야·생태계·제품·인기 묶음 목록, ETag = 판 (같으면 304)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final TagCatalogProvider tagCatalogProvider;

    @GetMapping("/tags")
    public ResponseEntity<ApiResponse<CatalogTagsResponse>> getCatalogTags(
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {
        TagCatalog catalog = tagCatalogProvider.getCatalog();
        String eTag = "\"" + catalog.getVersion() + "\"";
        if (eTag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTag).build();
        }
        return ResponseEntity.ok().eTag(eTag).body(ApiResponse.of(CatalogTagsResponse.from(catalog)));
    }
}
