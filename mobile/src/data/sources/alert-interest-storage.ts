import {
  type AlertDigestSettings,
  type AlertInterests,
  DEFAULT_ALERT_DIGEST_SETTINGS,
  EMPTY_ALERT_INTERESTS,
  toAlertDigestSettings,
  toAlertInterests,
} from '../models/alert-interest';
import { DEVICE_STORAGE_KEYS, readStoredJson, saveStoredJson } from './device-json-storage';

/** 기기에 저장된 관심 기술 (없음·깨진 값 > 고른 것 없음) */
export async function readStoredAlertInterests(): Promise<AlertInterests> {
  return readStoredJson(DEVICE_STORAGE_KEYS.alertInterests, (parsedValue) => toAlertInterests(parsedValue) ?? EMPTY_ALERT_INTERESTS, EMPTY_ALERT_INTERESTS);
}

export async function saveStoredAlertInterests(interests: AlertInterests): Promise<void> {
  await saveStoredJson(DEVICE_STORAGE_KEYS.alertInterests, interests);
}

/** 기기에 저장된 받는 방식 (없음 > 아침 요약 08:00) */
export async function readStoredAlertDigestSettings(): Promise<AlertDigestSettings> {
  return readStoredJson(
    DEVICE_STORAGE_KEYS.alertDigest,
    (parsedValue) => toAlertDigestSettings(parsedValue) ?? DEFAULT_ALERT_DIGEST_SETTINGS,
    DEFAULT_ALERT_DIGEST_SETTINGS,
  );
}

export async function saveStoredAlertDigestSettings(digestSettings: AlertDigestSettings): Promise<void> {
  await saveStoredJson(DEVICE_STORAGE_KEYS.alertDigest, digestSettings);
}

/** 관심 기술 처음 안내를 닫았는지 */
export async function readIsInterestPromptDismissed(): Promise<boolean> {
  return readStoredJson(DEVICE_STORAGE_KEYS.interestPromptDismissed, (parsedValue) => parsedValue === true, false);
}

export async function saveInterestPromptDismissed(): Promise<void> {
  await saveStoredJson(DEVICE_STORAGE_KEYS.interestPromptDismissed, true);
}
