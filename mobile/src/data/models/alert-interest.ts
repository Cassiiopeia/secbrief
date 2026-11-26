import { isTimeOfDay } from './quiet-hours';

/**
 * 기기 관심 기술 (#93): 카탈로그 code 만 담음 (이름은 카탈로그에서 찾음)
 * 지워진 code 는 서버가 알림 판정에서 무시하고, 앱은 그리지 않음
 */
export interface AlertInterests {
  productCodes: string[];
  fieldCodes: string[];
  ecosystemCodes: string[];
}

/** 관심 소식 받는 방식 (서버 AlertDeliveryMode 이름 그대로): IMMEDIATE 바로 받기, DIGEST 아침 요약 */
export const ALERT_DELIVERY_MODES = ['IMMEDIATE', 'DIGEST'] as const;
export type AlertDeliveryMode = (typeof ALERT_DELIVERY_MODES)[number];

/** 급하지 않은 관심 소식 받는 방식 + 아침 요약 시각("HH:mm", 기기 시간대) */
export interface AlertDigestSettings {
  deliveryMode: AlertDeliveryMode;
  digestTime: string;
}

export const EMPTY_ALERT_INTERESTS: AlertInterests = { productCodes: [], fieldCodes: [], ecosystemCodes: [] };

/** 기본값: 아침 요약 08:00 (서버 기본값과 같음) */
export const DEFAULT_ALERT_DIGEST_SETTINGS: AlertDigestSettings = { deliveryMode: 'DIGEST', digestTime: '08:00' };

/** 관심 기술을 하나라도 골랐는지 */
export function hasAnyAlertInterest(interests: AlertInterests): boolean {
  return interests.productCodes.length + interests.fieldCodes.length + interests.ecosystemCodes.length > 0;
}

/** 고른 관심 기술 수 (제품 + 분야 + 생태계) */
export function countAlertInterests(interests: AlertInterests): number {
  return interests.productCodes.length + interests.fieldCodes.length + interests.ecosystemCodes.length;
}

/** 임의의 값(서버 응답·저장값) > 관심 기술 (글자 아닌 값·중복 제거, 객체가 아니면 없음) */
export function toAlertInterests(value: unknown): AlertInterests | undefined {
  if (typeof value !== 'object' || value === null) return undefined;
  const { productCodes, fieldCodes, ecosystemCodes } = value as Record<string, unknown>;
  return { productCodes: toCodes(productCodes), fieldCodes: toCodes(fieldCodes), ecosystemCodes: toCodes(ecosystemCodes) };
}

/** 임의의 값 > 받는 방식 (모르는 방식·시각은 기본값, 객체가 아니면 없음) */
export function toAlertDigestSettings(value: unknown): AlertDigestSettings | undefined {
  if (typeof value !== 'object' || value === null) return undefined;
  const { deliveryMode, digestTime } = value as Record<string, unknown>;
  return {
    deliveryMode: ALERT_DELIVERY_MODES.find((mode) => mode === deliveryMode) ?? DEFAULT_ALERT_DIGEST_SETTINGS.deliveryMode,
    digestTime: isTimeOfDay(digestTime) ? digestTime : DEFAULT_ALERT_DIGEST_SETTINGS.digestTime,
  };
}

function toCodes(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [...new Set(value.filter((code): code is string => typeof code === 'string' && code.length > 0))];
}
