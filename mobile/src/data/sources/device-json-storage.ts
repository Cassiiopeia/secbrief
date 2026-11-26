/**
 * 기기 저장소 공통: 저장 키 목록, JSON 읽기(실패 시 기본값)·쓰기
 * AsyncStorage 는 이 파일에서만 부름 (tests/architecture 가 확인)
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

// 기기 저장소 키 4개 (값은 JSON, 기기에만 두고 서버로 보내지 않음)
export const DEVICE_STORAGE_KEYS = {
  // 방해 금지 시간 (예: {"isEnabled":true,"startTime":"22:00","endTime":"08:00","timeZone":"Asia/Seoul"})
  quietHours: 'secbrief.quietHours',
  // 켠 알림 종류 (배열, 예: ["KEV","CRITICAL_CVE"])
  enabledAlertCategories: 'secbrief.enabledAlertCategories',
  // 알림함 읽음 기록 (예: {"seenUpToIdByKind":{"ALERT":42},"openedNotificationIds":[40,42]})
  notificationReadState: 'secbrief.notificationReadState',
  // 읽은 기사 ID (배열, 최근에 읽은 것이 뒤)
  readArticleIds: 'secbrief.readArticleIds',
} as const;

export type DeviceStorageKey = (typeof DEVICE_STORAGE_KEYS)[keyof typeof DEVICE_STORAGE_KEYS];

/** 저장값 읽기: 없음·읽기 실패·깨진 JSON·변환 실패 > fallbackValue, 있으면 toStoredValue(파싱한 값) */
export async function readStoredJson<T>(
  storageKey: DeviceStorageKey,
  toStoredValue: (parsedValue: unknown) => T,
  fallbackValue: T,
): Promise<T> {
  try {
    const storedText = await AsyncStorage.getItem(storageKey);
    return storedText === null ? fallbackValue : toStoredValue(JSON.parse(storedText));
  } catch {
    return fallbackValue;
  }
}

/** 값 저장 (JSON 글자로) */
export async function saveStoredJson(storageKey: DeviceStorageKey, value: unknown): Promise<void> {
  await AsyncStorage.setItem(storageKey, JSON.stringify(value));
}
