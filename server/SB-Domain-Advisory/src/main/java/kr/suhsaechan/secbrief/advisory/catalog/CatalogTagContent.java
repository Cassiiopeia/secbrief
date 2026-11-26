package kr.suhsaechan.secbrief.advisory.catalog;

/**
 * 분류 태그의 바꿀 수 있는 값 (관리자 화면 입력): 이름, 짧은 이름, 묶음, 분야, 분류 낱말, 널리 쓰는지, 순서
 */
public record CatalogTagContent(String name, String shortName, String groupCode, String fieldCode,
                                String matchTerms, boolean isWidelyUsed, int displayOrder) {
}
