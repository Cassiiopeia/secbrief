import AsyncStorage from '@react-native-async-storage/async-storage';

import { type AlertCategory, toAlertCategories } from '../models/alert-category';

// 기기 저장소 키: 켠 알림 종류 (JSON 배열, 예: ["KEV","CRITICAL_CVE"])
const ENABLED_ALERT_CATEGORIES_STORAGE_KEY = 'secbrief.enabledAlertCategories';

/** 기기에 저장된 켠 알림 종류 (없음·읽기 실패·깨진 값 > 빈 목록) */
export async function readEnabledAlertCategories(): Promise<AlertCategory[]> {
  try {
    const storedText = await AsyncStorage.getItem(ENABLED_ALERT_CATEGORIES_STORAGE_KEY);
    return storedText === null ? [] : toAlertCategories(JSON.parse(storedText));
  } catch {
    return [];
  }
}

/** 켠 알림 종류 저장 */
export async function saveEnabledAlertCategories(enabledAlertCategories: AlertCategory[]): Promise<void> {
  await AsyncStorage.setItem(ENABLED_ALERT_CATEGORIES_STORAGE_KEY, JSON.stringify(enabledAlertCategories));
}
