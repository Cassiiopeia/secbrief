import type { AlertDigestSettings, AlertInterests } from './alert-interest';
import type { QuietHours } from './quiet-hours';

/**
 * 알림 종류 (서버 enum 이름 그대로, 화면 표시 순서) 4개: KISA 긴급, 악용 중, 고위험(그 밖의 고위험), INTEREST 관심 기술 (#93)
 * INTEREST 는 알림함 묶음 종류로만 오고 기기가 켜고 끄지 않음 (관심 기술 목록으로 정함)
 */
export const ALERT_CATEGORIES = ['KISA_URGENT', 'KEV', 'CRITICAL_CVE', 'INTEREST'] as const;
export type AlertCategory = (typeof ALERT_CATEGORIES)[number];

/** 기기가 스위치로 켜고 끄는 종류 3개 (설정 화면 순서) */
export const SWITCHABLE_ALERT_CATEGORIES = ['KEV', 'KISA_URGENT', 'CRITICAL_CVE'] as const satisfies readonly AlertCategory[];
export type SwitchableAlertCategory = (typeof SWITCHABLE_ALERT_CATEGORIES)[number];

/** 푸시를 받는 기기 종류 (서버 enum 이름) */
export type PushPlatform = 'ANDROID' | 'IOS';

/**
 * 서버에 등록하는 기기 한 대: 푸시 토큰 + 켠 알림 종류 전체 (빈 목록이면 서버가 기기를 지움) + 방해 금지 시간
 * quietHours 가 없으면 서버가 지금 값을 그대로 둠
 */
export interface PushDeviceRegistration {
  pushToken: string;
  platform: PushPlatform;
  enabledAlertCategories: SwitchableAlertCategory[];
  quietHours?: QuietHours;
  /** 관심 기술 (#93, 없으면 서버가 지금 값 유지) */
  interests?: AlertInterests;
  /** 관심 소식 받는 방식 (#93, 없으면 서버가 지금 값 유지) */
  digest?: AlertDigestSettings;
}

/** 서버에 저장된 기기 설정: 켠 알림 종류 + 방해 금지 시간·관심 기술·받는 방식 (이 필드가 생기기 전 서버는 주지 않음) */
export interface PushDeviceSettings {
  enabledAlertCategories: SwitchableAlertCategory[];
  quietHours?: QuietHours;
  interests?: AlertInterests;
  digest?: AlertDigestSettings;
}

/** 임의의 값 > 켜고 끄는 알림 종류 목록 (모르는 값·중복 제거, 표시 순서로 정렬) */
export function toAlertCategories(value: unknown): SwitchableAlertCategory[] {
  if (!Array.isArray(value)) return [];
  return SWITCHABLE_ALERT_CATEGORIES.filter((category) => value.includes(category));
}
