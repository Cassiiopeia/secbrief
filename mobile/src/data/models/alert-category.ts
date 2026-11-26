import type { QuietHours } from './quiet-hours';

/** 긴급 알림 종류 (서버 enum 이름 그대로, 화면 표시 순서) */
export const ALERT_CATEGORIES = ['KISA_URGENT', 'KEV', 'CRITICAL_CVE'] as const;
export type AlertCategory = (typeof ALERT_CATEGORIES)[number];

/** 푸시를 받는 기기 종류 (서버 enum 이름) */
export type PushPlatform = 'ANDROID' | 'IOS';

/**
 * 서버에 등록하는 기기 한 대: 푸시 토큰 + 켠 알림 종류 전체 (빈 목록이면 서버가 기기를 지움) + 방해 금지 시간
 * quietHours 가 없으면 서버가 지금 값을 그대로 둠
 */
export interface PushDeviceRegistration {
  pushToken: string;
  platform: PushPlatform;
  enabledAlertCategories: AlertCategory[];
  quietHours?: QuietHours;
}

/** 서버에 저장된 기기 설정: 켠 알림 종류 + 방해 금지 시간 (이 필드가 생기기 전 서버는 주지 않음) */
export interface PushDeviceSettings {
  enabledAlertCategories: AlertCategory[];
  quietHours?: QuietHours;
}

/** 임의의 값 > 알림 종류 목록 (모르는 값·중복 제거, 표시 순서로 정렬) */
export function toAlertCategories(value: unknown): AlertCategory[] {
  if (!Array.isArray(value)) return [];
  return ALERT_CATEGORIES.filter((category) => value.includes(category));
}
