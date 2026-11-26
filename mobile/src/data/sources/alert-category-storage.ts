import { type SwitchableAlertCategory, toAlertCategories } from '../models/alert-category';
import { DEVICE_STORAGE_KEYS, readStoredJson, saveStoredJson } from './device-json-storage';

/** 기기에 저장된 켠 알림 종류 (없음·읽기 실패·깨진 값 > 빈 목록) */
export async function readEnabledAlertCategories(): Promise<SwitchableAlertCategory[]> {
  return readStoredJson(DEVICE_STORAGE_KEYS.enabledAlertCategories, toAlertCategories, []);
}

/** 켠 알림 종류 저장 */
export async function saveEnabledAlertCategories(enabledAlertCategories: SwitchableAlertCategory[]): Promise<void> {
  await saveStoredJson(DEVICE_STORAGE_KEYS.enabledAlertCategories, enabledAlertCategories);
}
