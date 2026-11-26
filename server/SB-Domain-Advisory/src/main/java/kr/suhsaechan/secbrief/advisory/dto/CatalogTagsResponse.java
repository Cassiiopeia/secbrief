package kr.suhsaechan.secbrief.advisory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.catalog.CatalogEntry;
import kr.suhsaechan.secbrief.advisory.catalog.TagCatalog;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;

/**
 * 분류 태그 목록 (보이는 항목만, 종류 > 순서): 판(version, 바뀌면 앱이 다시 받음) + 항목 (분류 낱말은 내보내지 않음)
 */
public record CatalogTagsResponse(String version, List<CatalogTagResponse> tags) {

    public static CatalogTagsResponse from(TagCatalog catalog) {
        return new CatalogTagsResponse(catalog.getVersion(),
                catalog.getEntries().stream().map(CatalogTagResponse::from).toList());
    }

    /** 항목 1개: 종류, code, 이름, 짧은 이름, 묶음(PRODUCT), 분야(PRODUCT), 순서, 널리 쓰는지 */
    public record CatalogTagResponse(CatalogTagKind kind, String code, String name, String shortName, String groupCode,
                                     String fieldCode, int displayOrder,
                                     @JsonProperty("isWidelyUsed") boolean isWidelyUsed) {

        static CatalogTagResponse from(CatalogEntry entry) {
            return new CatalogTagResponse(entry.kind(), entry.code(), entry.name(), entry.shortName(), entry.groupCode(),
                    entry.fieldCode(), entry.displayOrder(), entry.isWidelyUsed());
        }
    }
}
