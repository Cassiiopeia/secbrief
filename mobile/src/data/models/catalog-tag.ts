/**
 * 분류 태그 카탈로그 (#93): 기술 분야·생태계·제품 묶음·제품. 값은 서버 DB 가 단일 원본이라 앱에 목록을 두지 않음
 * kind 값 이름만 서버 CatalogTagKind 와 같음 (서버 <-> 앱 계약 테스트)
 */
export const CATALOG_TAG_KINDS = ['FIELD', 'ECOSYSTEM', 'PRODUCT_GROUP', 'PRODUCT'] as const;
export type CatalogTagKind = (typeof CATALOG_TAG_KINDS)[number];

/** 카탈로그 항목 1개 (code 는 바뀌지 않음, 이름은 바뀔 수 있음) */
export interface CatalogTag {
  kind: CatalogTagKind;
  code: string;
  name: string;
  /** 칩·태그용 짧은 이름 (없으면 name) */
  shortName: string;
  /** PRODUCT 의 인기 묶음 code (없으면 검색으로만 찾음) */
  groupCode?: string;
  /** PRODUCT 의 기술 분야 code */
  fieldCode?: string;
  displayOrder: number;
  /** 주요 판정의 널리 쓰는 제품 */
  isWidelyUsed: boolean;
}

/** 카탈로그 전체: 서버 버전(ETag 값) + 보이는 항목 */
export interface TagCatalog {
  version: string;
  tags: CatalogTag[];
}

export const EMPTY_TAG_CATALOG: TagCatalog = { version: '', tags: [] };
