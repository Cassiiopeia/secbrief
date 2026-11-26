import { toTagCatalog } from '../mappers/catalog-tag-mapper';
import { EMPTY_TAG_CATALOG, type TagCatalog } from '../models/catalog-tag';
import { DEVICE_STORAGE_KEYS, readStoredJson, saveStoredJson } from './device-json-storage';
import { fetchApiDataIfChanged } from './secbrief-api-client';

/** 기기에 저장된 카탈로그 사본 (없음·깨진 값 > 빈 카탈로그) */
export async function readStoredTagCatalog(): Promise<TagCatalog> {
  return readStoredJson(DEVICE_STORAGE_KEYS.tagCatalog, (parsedValue) => toTagCatalog(parsedValue) ?? EMPTY_TAG_CATALOG, EMPTY_TAG_CATALOG);
}

/**
 * 카탈로그 새로 받기: 저장본 버전을 If-None-Match 로 보냄 > 304 면 저장본, 바뀌었으면 새 값 저장 후 돌려줌
 * GET /api/v1/catalog/tags (ETag 는 따옴표 붙인 version)
 */
export async function refreshTagCatalog(): Promise<TagCatalog> {
  const storedCatalog = await readStoredTagCatalog();
  const entityTag = storedCatalog.version.length > 0 ? `"${storedCatalog.version}"` : undefined;
  const catalogResponse = await fetchApiDataIfChanged<unknown>('/api/v1/catalog/tags', entityTag);
  if (catalogResponse.isNotModified) return storedCatalog;
  const freshCatalog = toTagCatalog(catalogResponse.responseData) ?? storedCatalog;
  await saveStoredJson(DEVICE_STORAGE_KEYS.tagCatalog, freshCatalog);
  return freshCatalog;
}
