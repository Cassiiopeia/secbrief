/**
 * 알림함·방해 금지 시간 날짜·시각 표기 (앱 공통 포맷터, 화면에서 직접 조립하지 않음)
 */
// Date.getDay(): 0 일요일 ~ 6 토요일
const KOREAN_WEEKDAY_LABELS = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'] as const;
const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;
const HOURS_PER_HALF_DAY = 12;

const toDayStart = (dateTime: Date): number => new Date(dateTime.getFullYear(), dateTime.getMonth(), dateTime.getDate()).getTime();

/** 시각 > 기기 달력 날짜 키 (예: 2026-11-27, 날짜별 묶음 기준) */
export function toLocalDayKey(dateTime: Date): string {
  const month = String(dateTime.getMonth() + 1).padStart(2, '0');
  const day = String(dateTime.getDate()).padStart(2, '0');
  return `${dateTime.getFullYear()}-${month}-${day}`;
}

/** 시각 > 날짜 묶음 이름: 오늘, 어제, 올해는 11월 25일 화요일, 그 전은 2025년 11월 25일 */
export function toDayGroupLabel(dateTime: Date, now: Date): string {
  const elapsedDays = Math.round((toDayStart(now) - toDayStart(dateTime)) / MILLISECONDS_PER_DAY);
  if (elapsedDays <= 0) return '오늘';
  if (elapsedDays === 1) return '어제';
  const monthDay = `${dateTime.getMonth() + 1}월 ${dateTime.getDate()}일`;
  if (dateTime.getFullYear() === now.getFullYear()) return `${monthDay} ${KOREAN_WEEKDAY_LABELS[dateTime.getDay()]}`;
  return `${dateTime.getFullYear()}년 ${monthDay}`;
}

/** "HH:mm" > 오전·오후 표기 (예: 22:00 > 오후 10:00, 00:30 > 오전 12:30, 12:00 > 오후 12:00) */
export function toKoreanClockTime(timeOfDay: string): string {
  const [hourText, minuteText] = timeOfDay.split(':');
  const hour = Number(hourText);
  const halfDayLabel = hour < HOURS_PER_HALF_DAY ? '오전' : '오후';
  const halfDayHour = hour % HOURS_PER_HALF_DAY === 0 ? HOURS_PER_HALF_DAY : hour % HOURS_PER_HALF_DAY;
  return `${halfDayLabel} ${halfDayHour}:${minuteText}`;
}
