import type { AlertEstimate } from '@/data/models/alert-estimate';
import type { AlertInterests } from '@/data/models/alert-interest';
import type { CatalogTag, CatalogTagKind } from '@/data/models/catalog-tag';
import type { SbPickOption } from '@/design';

import type { TagCatalogIndex } from '../catalog/tag-catalog-view';

/** 관심 기술 화면 칸 3개: 제품, 분야, 생태계 */
export type InterestPickerTab = 'product' | 'field' | 'ecosystem';

/** 인기 묶음 1개 (카탈로그 PRODUCT_GROUP 순서, 제품은 카탈로그 순서) */
export interface ProductGroupSection {
  groupCode: string;
  title: string;
  options: SbPickOption[];
}

// 요약 줄에 이름을 보이는 수 (넘으면 외 N개)
const SUMMARY_NAME_COUNT = 2;
const NOTHING_PICKED_LABEL = '아직 안 골랐어요';
// 검색 결과 최대 수
const SEARCH_RESULT_MAXIMUM_COUNT = 30;

/** 관심 기술 칸 > 그 칸의 code 목록 이름 */
export const INTEREST_CODES_KEY_BY_TAB = { product: 'productCodes', field: 'fieldCodes', ecosystem: 'ecosystemCodes' } as const satisfies Record<
  InterestPickerTab,
  keyof AlertInterests
>;
const CATALOG_KIND_BY_TAB: Record<InterestPickerTab, CatalogTagKind> = { product: 'PRODUCT', field: 'FIELD', ecosystem: 'ECOSYSTEM' };

/** code 하나 넣기·빼기 (순서 유지) */
export function toToggledInterests(interests: AlertInterests, tab: InterestPickerTab, code: string): AlertInterests {
  const key = INTEREST_CODES_KEY_BY_TAB[tab];
  const codes = interests[key];
  return { ...interests, [key]: codes.includes(code) ? codes.filter((pickedCode) => pickedCode !== code) : [...codes, code] };
}

/** 고른 code > 카탈로그 이름 (지워진 code 는 빼고) */
export function toPickedNames(interests: AlertInterests, tab: InterestPickerTab, index: TagCatalogIndex): string[] {
  return interests[INTEREST_CODES_KEY_BY_TAB[tab]]
    .map((code) => index.findTag(CATALOG_KIND_BY_TAB[tab], code)?.shortName)
    .filter((name): name is string => name !== undefined);
}

/** 고른 것 수 (카탈로그에 있는 것만) */
export function countPickedInterests(interests: AlertInterests, index: TagCatalogIndex): number {
  return (['product', 'field', 'ecosystem'] as const).reduce((sum, tab) => sum + toPickedNames(interests, tab, index).length, 0);
}

/** 더보기 요약 3줄: 제품 Windows, Chrome 외 4개 / 분야 … / 생태계 … */
export function toInterestSummaryLines(interests: AlertInterests, index: TagCatalogIndex): string[] {
  return [
    `제품 ${toNamesLabel(toPickedNames(interests, 'product', index))}`,
    `분야 ${toNamesLabel(toPickedNames(interests, 'field', index))}`,
    `생태계 ${toNamesLabel(toPickedNames(interests, 'ecosystem', index))}`,
  ];
}

function toNamesLabel(names: string[]): string {
  if (names.length === 0) return NOTHING_PICKED_LABEL;
  const shownNames = names.slice(0, SUMMARY_NAME_COUNT).join(', ');
  return names.length > SUMMARY_NAME_COUNT ? `${shownNames} 외 ${names.length - SUMMARY_NAME_COUNT}개` : shownNames;
}

/** 인기 묶음 (묶음이 있는 제품만, 빈 묶음은 뺌) */
export function toProductGroupSections(index: TagCatalogIndex): ProductGroupSection[] {
  return index.productGroups
    .map((group) => ({
      groupCode: group.code,
      title: group.name,
      options: index.products.filter((product) => product.groupCode === group.code).map(toPickOption),
    }))
    .filter((section) => section.options.length > 0);
}

/** 고른 제품 중 인기 묶음에 없는 것 (검색으로 고른 것: 화면 위에 따로 보여 줌) */
export function toPickedProductsOutsideGroups(interests: AlertInterests, index: TagCatalogIndex): SbPickOption[] {
  const groupCodes = new Set(index.productGroups.map((group) => group.code));
  return interests.productCodes
    .map((code) => index.findTag('PRODUCT', code))
    .filter((product): product is CatalogTag => product !== undefined && (product.groupCode === undefined || !groupCodes.has(product.groupCode)))
    .map(toPickOption);
}

/** 제품 검색: 이름·짧은 이름에 글자가 들어 있는 것 (대소문자 무시, 최대 30개) */
export function searchProducts(index: TagCatalogIndex, query: string): SbPickOption[] {
  const normalizedQuery = query.trim().toLowerCase();
  if (normalizedQuery.length === 0) return [];
  return index.products
    .filter((product) => product.name.toLowerCase().includes(normalizedQuery) || product.shortName.toLowerCase().includes(normalizedQuery))
    .slice(0, SEARCH_RESULT_MAXIMUM_COUNT)
    .map(toPickOption);
}

function toPickOption(tag: CatalogTag): SbPickOption {
  return { value: tag.code, label: tag.name };
}

/** 화면 아래 한 줄: 제품 6 · 분야 1 · 생태계 1 · 어제 기준 알림 4번 (지금은 7번), 추정 전이면 고른 수만 */
export function toEstimateLine(interests: AlertInterests, index: TagCatalogIndex, estimate: AlertEstimate | undefined): string {
  const pickedLine = (['product', 'field', 'ecosystem'] as const)
    .map((tab) => `${TAB_LABELS[tab]} ${toPickedNames(interests, tab, index).length}`)
    .join(' · ');
  if (estimate === undefined) return pickedLine;
  const digestPart = estimate.digestPushCount > 0 ? ` + 아침 요약 ${estimate.digestPushCount}번` : '';
  return `${pickedLine} · 어제 기준 알림 ${estimate.immediatePushCount}번${digestPart} (지금은 ${estimate.currentPushCount}번)`;
}

/** 칸 이름 (세그먼트) */
export const TAB_LABELS: Record<InterestPickerTab, string> = { product: '제품', field: '분야', ecosystem: '생태계' };
