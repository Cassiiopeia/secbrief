import AsyncStorage from '@react-native-async-storage/async-storage';

import { DEFAULT_QUIET_HOURS, type QuietHours, toQuietHours } from '../models/quiet-hours';

// 기기 저장소 키: 방해 금지 시간 (JSON, 예: {"isEnabled":true,"startTime":"22:00","endTime":"08:00","timeZone":"Asia/Seoul"})
const QUIET_HOURS_STORAGE_KEY = 'secbrief.quietHours';

/** 기기에 저장된 방해 금지 시간 (없음·읽기 실패·깨진 값 > 기본값: 꺼짐) */
export async function readStoredQuietHours(): Promise<QuietHours> {
  try {
    const storedText = await AsyncStorage.getItem(QUIET_HOURS_STORAGE_KEY);
    return (storedText === null ? undefined : toQuietHours(JSON.parse(storedText))) ?? DEFAULT_QUIET_HOURS;
  } catch {
    return DEFAULT_QUIET_HOURS;
  }
}

/** 방해 금지 시간 저장 */
export async function saveStoredQuietHours(quietHours: QuietHours): Promise<void> {
  await AsyncStorage.setItem(QUIET_HOURS_STORAGE_KEY, JSON.stringify(quietHours));
}
