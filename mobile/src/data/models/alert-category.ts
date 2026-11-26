/** 긴급 알림 종류 (서버 enum 이름 그대로, 화면 표시 순서) */
export const ALERT_CATEGORIES = ['KISA_URGENT', 'KEV', 'CRITICAL_CVE'] as const;
export type AlertCategory = (typeof ALERT_CATEGORIES)[number];

/** 푸시를 받는 기기 종류 (서버 enum 이름) */
export type PushPlatform = 'ANDROID' | 'IOS';

/** 서버에 등록하는 기기 한 대: 푸시 토큰 + 켠 알림 종류 전체 (빈 목록이면 서버가 기기를 지움) */
export interface PushDeviceRegistration {
  pushToken: string;
  platform: PushPlatform;
  enabledAlertCategories: AlertCategory[];
}

/** 임의의 값 > 알림 종류 목록 (모르는 값·중복 제거, 표시 순서로 정렬) */
export function toAlertCategories(value: unknown): AlertCategory[] {
  if (!Array.isArray(value)) return [];
  return ALERT_CATEGORIES.filter((category) => value.includes(category));
}
