import { CATALOG_TAG_KINDS, type CatalogTag, type CatalogTagKind, type TagCatalog } from '../models/catalog-tag';
import { isNonArrayObject } from './unknown-value-guards';

/**
 * 카탈로그 응답·저장값 > 카탈로그 (모양을 믿지 않고 읽음)
 * 모르는 kind·code 나 이름이 없는 항목은 버림 (새 서버가 kind 를 더해도 앱이 깨지지 않게), kind > 순서로 정렬
 */
export function toTagCatalog(value: unknown): TagCatalog | undefined {
  if (!isNonArrayObject(value) || !Array.isArray(value.tags)) return undefined;
  const tags = value.tags
    .map(toCatalogTag)
    .filter((tag): tag is CatalogTag => tag !== undefined)
    .sort((left, right) => CATALOG_TAG_KINDS.indexOf(left.kind) - CATALOG_TAG_KINDS.indexOf(right.kind) || left.displayOrder - right.displayOrder);
  return { version: typeof value.version === 'string' ? value.version : '', tags };
}

function toCatalogTag(value: unknown): CatalogTag | undefined {
  if (!isNonArrayObject(value)) return undefined;
  const kind = CATALOG_TAG_KINDS.find((tagKind): tagKind is CatalogTagKind => tagKind === value.kind);
  if (kind === undefined || !isNonBlankText(value.code) || !isNonBlankText(value.name)) return undefined;
  return {
    kind,
    code: value.code,
    name: value.name,
    shortName: isNonBlankText(value.shortName) ? value.shortName : value.name,
    groupCode: isNonBlankText(value.groupCode) ? value.groupCode : undefined,
    fieldCode: isNonBlankText(value.fieldCode) ? value.fieldCode : undefined,
    displayOrder: typeof value.displayOrder === 'number' ? value.displayOrder : 0,
    isWidelyUsed: value.isWidelyUsed === true,
  };
}

function isNonBlankText(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
