import type { BriefingItem } from '@/data/models/briefing-item';
import type { CatalogTag, CatalogTagKind, TagCatalog } from '@/data/models/catalog-tag';
import type { SbBadgeContent } from '@/design';

/** 카탈로그 색인: 종류별 목록(순서 그대로) + code 로 찾기 */
export interface TagCatalogIndex {
  version: string;
  fields: CatalogTag[];
  ecosystems: CatalogTag[];
  productGroups: CatalogTag[];
  products: CatalogTag[];
  findTag: (kind: CatalogTagKind, code: string | undefined) => CatalogTag | undefined;
}

// 주요 이유 배지 문구 (서버 주요 판정 규칙의 사람 말, #93)
const WIDELY_USED_REASON = '널리 씀';
const CRITICAL_SCORE_REASON = 'CVSS 9.0 이상';
const AI_HEADLINE_REASON = 'AI 중요';
const REASON_SEPARATOR = ' · ';
const CRITICAL_MINIMUM_SCORE = 9;
// 목록 한 건에 보이는 분류 태그 배지 수 한도
const TAG_BADGE_MAXIMUM_COUNT = 3;

/** 카탈로그 > 색인 (모르는 code 는 찾지 못함: 그 태그는 그리지 않음) */
export function toTagCatalogIndex(catalog: TagCatalog): TagCatalogIndex {
  const tagByKey = new Map(catalog.tags.map((tag) => [`${tag.kind}:${tag.code}`, tag]));
  const ofKind = (kind: CatalogTagKind) => catalog.tags.filter((tag) => tag.kind === kind);
  return {
    version: catalog.version,
    fields: ofKind('FIELD'),
    ecosystems: ofKind('ECOSYSTEM'),
    productGroups: ofKind('PRODUCT_GROUP'),
    products: ofKind('PRODUCT'),
    findTag: (kind, code) => (code === undefined ? undefined : tagByKey.get(`${kind}:${code}`)),
  };
}

/** code > 짧은 이름 (모르는 code 는 없음) */
export function toTagShortName(index: TagCatalogIndex, kind: CatalogTagKind, code: string | undefined): string | undefined {
  return index.findTag(kind, code)?.shortName;
}

/**
 * 소식 > 분류 태그 배지 (최대 3개): 생태계 > 분야 > 주요 이유(주요면) 또는 제품(카탈로그 이름, 없으면 제목 속 이름)
 * 카탈로그에 없는 code 는 빼고 그림
 */
export function toTagBadges(item: BriefingItem, index: TagCatalogIndex): SbBadgeContent[] {
  const badges: SbBadgeContent[] = [];
  const ecosystemName = toTagShortName(index, 'ECOSYSTEM', item.ecosystemCode);
  const fieldName = toTagShortName(index, 'FIELD', item.fieldCode);
  const product = index.findTag('PRODUCT', item.productCode);
  const productName = product?.shortName ?? item.subjectName;
  if (ecosystemName !== undefined) badges.push({ label: ecosystemName, tone: 'neutral' });
  if (fieldName !== undefined) badges.push({ label: fieldName, tone: 'neutral' });
  const notableReason = toNotableReason(item, product);
  if (notableReason !== undefined) badges.push({ label: notableReason, tone: 'notice' });
  else if (productName !== undefined && ecosystemName === undefined) badges.push({ label: productName, tone: 'subject' });
  return badges.slice(0, TAG_BADGE_MAXIMUM_COUNT);
}

/** 주요 소식의 이유 (KEV 는 악용 중 배지가 따로 있어 뺌): 널리 쓰는 제품 / CVSS 9.0 이상 · AI 중요 */
export function toNotableReason(item: BriefingItem, product: CatalogTag | undefined): string | undefined {
  if (!item.isNotable) return undefined;
  if (product?.isWidelyUsed === true) return WIDELY_USED_REASON;
  const reasons = [
    item.cvssScore !== undefined && item.cvssScore >= CRITICAL_MINIMUM_SCORE ? CRITICAL_SCORE_REASON : undefined,
    item.importance === 'headline' ? AI_HEADLINE_REASON : undefined,
  ].filter((reason): reason is string => reason !== undefined);
  return reasons.length === 0 ? undefined : reasons.join(REASON_SEPARATOR);
}
