package kr.suhsaechan.secbrief.advisory.constant;

/**
 * 분류 태그 종류 4개 (이름이 그대로 API 값·DB 값): 값 목록 자체는 catalog_tag 행 (관리자 화면에서 바꿈)
 * FIELD 기술 분야, ECOSYSTEM 오픈소스 생태계, PRODUCT_GROUP 관심 기술 인기 묶음, PRODUCT 제품·회사·패키지
 */
public enum CatalogTagKind {
    FIELD,
    ECOSYSTEM,
    PRODUCT_GROUP,
    PRODUCT
}
