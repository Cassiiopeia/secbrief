import AsyncStorage from '@react-native-async-storage/async-storage';

import {
  type AppNotificationReadState,
  EMPTY_NOTIFICATION_READ_STATE,
  toNotificationReadState,
} from '../models/app-notification-read-state';

// 기기 저장소 키: 알림함 읽음 기록 (JSON, 예: {"seenUpToIdByKind":{"ALERT":42},"openedNotificationIds":[40,42]})
const NOTIFICATION_READ_STATE_STORAGE_KEY = 'secbrief.notificationReadState';

/** 기기에 저장된 알림함 읽음 기록 (없음·읽기 실패·깨진 값 > 빈 기록, 깨진 칸만 버림) */
export async function readNotificationReadState(): Promise<AppNotificationReadState> {
  try {
    const storedText = await AsyncStorage.getItem(NOTIFICATION_READ_STATE_STORAGE_KEY);
    return storedText === null ? EMPTY_NOTIFICATION_READ_STATE : toNotificationReadState(JSON.parse(storedText));
  } catch {
    return EMPTY_NOTIFICATION_READ_STATE;
  }
}

/** 알림함 읽음 기록 저장 */
export async function saveNotificationReadState(readState: AppNotificationReadState): Promise<void> {
  await AsyncStorage.setItem(NOTIFICATION_READ_STATE_STORAGE_KEY, JSON.stringify(readState));
}
