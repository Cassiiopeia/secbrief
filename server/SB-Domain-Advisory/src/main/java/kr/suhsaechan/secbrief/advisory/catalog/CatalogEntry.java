package kr.suhsaechan.secbrief.advisory.catalog;

import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;

/**
 * 스냅샷 안의 분류 태그 1개 (읽기 전용): 화면 값 + 분류 낱말 일치기
 */
public record CatalogEntry(CatalogTagKind kind, String code, String name, String shortName, String groupCode,
                           String fieldCode, boolean isWidelyUsed, int displayOrder, String matchTerms,
                           CatalogTermMatcher termMatcher) {

    public static CatalogEntry from(CatalogTag catalogTag) {
        return of(catalogTag.getKind(), catalogTag.getCode(), catalogTag.getName(), catalogTag.getShortName(),
                catalogTag.getGroupCode(), catalogTag.getFieldCode(), catalogTag.isWidelyUsed(),
                catalogTag.getDisplayOrder(), catalogTag.getMatchTerms());
    }

    /** 값 > 항목 (낱말 일치기를 함께 만듦) */
    public static CatalogEntry of(CatalogTagKind kind, String code, String name, String shortName, String groupCode,
                                  String fieldCode, boolean isWidelyUsed, int displayOrder, String matchTerms) {
        return new CatalogEntry(kind, code, name, shortName, groupCode, fieldCode, isWidelyUsed, displayOrder,
                matchTerms == null ? "" : matchTerms, CatalogTermMatcher.of(matchTerms));
    }
}
