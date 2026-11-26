import { DEFAULT_QUIET_HOURS, type QuietHours, toQuietHours } from '../models/quiet-hours';
import { DEVICE_STORAGE_KEYS, readStoredJson, saveStoredJson } from './device-json-storage';

/** 기기에 저장된 방해 금지 시간 (없음·읽기 실패·깨진 값 > 기본값: 꺼짐) */
export async function readStoredQuietHours(): Promise<QuietHours> {
  return readStoredJson(
    DEVICE_STORAGE_KEYS.quietHours,
    (parsedValue) => toQuietHours(parsedValue) ?? DEFAULT_QUIET_HOURS,
    DEFAULT_QUIET_HOURS,
  );
}

/** 방해 금지 시간 저장 */
export async function saveStoredQuietHours(quietHours: QuietHours): Promise<void> {
  await saveStoredJson(DEVICE_STORAGE_KEYS.quietHours, quietHours);
}
