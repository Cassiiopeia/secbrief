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

const MILLISECONDS_PER_MINUTE = 60_000;
const MINUTES_PER_HOUR = 60;
const HOURS_PER_DAY = 24;

/** 시각 > 절대 날짜 (예: 2026.11.23 09:12) */
export function toAbsoluteDateTime(dateTime: Date): string {
  const datePart = `${dateTime.getFullYear()}.${toTwoDigits(dateTime.getMonth() + 1)}.${toTwoDigits(dateTime.getDate())}`;
  return `${datePart} ${toClockTime(dateTime)}`;
}

/** 지난 시간 > 상대 표기 (1분 미만·미래 방금, 60분 미만 N분 전, 24시간 미만 N시간 전, 그 외 N일 전) */
export function toRelativeTime(dateTime: Date, now: Date): string {
  const elapsedMinutes = Math.floor((now.getTime() - dateTime.getTime()) / MILLISECONDS_PER_MINUTE);
  if (elapsedMinutes < 1) return '방금';
  if (elapsedMinutes < MINUTES_PER_HOUR) return `${elapsedMinutes}분 전`;
  const elapsedHours = Math.floor(elapsedMinutes / MINUTES_PER_HOUR);
  if (elapsedHours < HOURS_PER_DAY) return `${elapsedHours}시간 전`;
  return `${Math.floor(elapsedHours / HOURS_PER_DAY)}일 전`;
}

/** 게시일 > 목록 표기: 절대 날짜 · 상대 표기 (예: 2026.11.23 09:12 · 2시간 전) */
export function toArticleDateLabel(publishedAt: Date, now: Date): string {
  return `${toAbsoluteDateTime(publishedAt)} · ${toRelativeTime(publishedAt, now)}`;
}
