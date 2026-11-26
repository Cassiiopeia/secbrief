import { DEFAULT_TIME_ZONE } from '../models/quiet-hours';

/** 기기 시간대 (IANA 이름, 예: Asia/Seoul). 알 수 없으면 Asia/Seoul */
export function getDeviceTimeZone(): string {
  try {
    const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return typeof timeZone === 'string' && timeZone.length > 0 ? timeZone : DEFAULT_TIME_ZONE;
  } catch {
    return DEFAULT_TIME_ZONE;
  }
}
