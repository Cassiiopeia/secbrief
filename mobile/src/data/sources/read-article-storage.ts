import { DEVICE_STORAGE_KEYS, readStoredJson, saveStoredJson } from './device-json-storage';

/** 기기에 저장된 읽은 기사 ID (없음·읽기 실패·깨진 값 > 빈 목록, 글자가 아닌 칸은 버림) */
export async function readReadArticleIds(): Promise<string[]> {
  return readStoredJson(DEVICE_STORAGE_KEYS.readArticleIds, toReadArticleIds, []);
}

/** 읽은 기사 ID 저장 */
export async function saveReadArticleIds(readArticleIds: string[]): Promise<void> {
  await saveStoredJson(DEVICE_STORAGE_KEYS.readArticleIds, readArticleIds);
}

// 저장값 > 글자 ID 목록 (배열이 아니면 빈 목록)
function toReadArticleIds(parsedValue: unknown): string[] {
  return Array.isArray(parsedValue) ? parsedValue.filter((id): id is string => typeof id === 'string') : [];
}
