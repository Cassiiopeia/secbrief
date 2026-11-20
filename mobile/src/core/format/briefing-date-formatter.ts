/**
 * 브리핑 화면용 날짜·시간 표기 (앱 공통 포맷터, 화면에서 직접 조립하지 않음)
 */
// Date.getDay(): 0 일요일 ~ 6 토요일
const WEEKDAY_LABELS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'] as const;

const toTwoDigits = (number: number): string => String(number).padStart(2, '0');

/** 날짜 > 머리 라벨 (예: 11.20 FRI) */
export function toMastheadDate(date: Date): string {
  const weekdayLabel = WEEKDAY_LABELS[date.getDay()];
  return `${toTwoDigits(date.getMonth() + 1)}.${toTwoDigits(date.getDate())} ${weekdayLabel}`;
}

/** 시각 > 24시간 표기 (예: 09:12) */
export function toClockTime(dateTime: Date): string {
  return `${toTwoDigits(dateTime.getHours())}:${toTwoDigits(dateTime.getMinutes())}`;
}
