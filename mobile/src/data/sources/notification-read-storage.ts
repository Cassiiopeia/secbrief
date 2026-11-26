import {
  type AppNotificationReadState,
  EMPTY_NOTIFICATION_READ_STATE,
  toNotificationReadState,
} from '../models/app-notification-read-state';
import { DEVICE_STORAGE_KEYS, readStoredJson, saveStoredJson } from './device-json-storage';

/** 기기에 저장된 알림함 읽음 기록 (없음·읽기 실패·깨진 값 > 빈 기록, 깨진 칸만 버림) */
export async function readNotificationReadState(): Promise<AppNotificationReadState> {
  return readStoredJson(DEVICE_STORAGE_KEYS.notificationReadState, toNotificationReadState, EMPTY_NOTIFICATION_READ_STATE);
}

/** 알림함 읽음 기록 저장 */
export async function saveNotificationReadState(readState: AppNotificationReadState): Promise<void> {
  await saveStoredJson(DEVICE_STORAGE_KEYS.notificationReadState, readState);
}
