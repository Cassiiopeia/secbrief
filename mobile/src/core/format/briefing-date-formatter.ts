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

/** 날짜 > 한국어 월·일 (예: 10월 22일) */
export function toKoreanMonthDay(date: Date): string {
  return `${date.getMonth() + 1}월 ${date.getDate()}일`;
}

/** 날짜 > 점 찍은 짧은 월·일 (예: 10.22) */
export function toDottedMonthDay(date: Date): string {
  return `${date.getMonth() + 1}.${toTwoDigits(date.getDate())}`;
}

/** 시각 > 24시간 표기 (예: 09:12) */
export function toClockTime(dateTime: Date): string {
  return `${toTwoDigits(dateTime.getHours())}:${toTwoDigits(dateTime.getMinutes())}`;
}

const MILLISECONDS_PER_MINUTE = 60_000;
const MINUTES_PER_HOUR = 60;
const HOURS_PER_DAY = 24;
const MILLISECONDS_PER_DAY = MILLISECONDS_PER_MINUTE * MINUTES_PER_HOUR * HOURS_PER_DAY;

// 날짜만 있는 게시일로 보는 UTC 시(분·초·밀리초는 0): 0 UTC 자정, 15 서울 자정
const DATE_ONLY_UTC_HOURS = [0, 15];

/** 시각 없이 날짜만 있는 값인지 (KISA·Debian 처럼 날짜만 주는 출처: 서버가 자정으로 채워 보냄) */
export function isDateOnly(dateTime: Date): boolean {
  const hasNoMinuteOrSmaller =
    dateTime.getUTCMinutes() === 0 && dateTime.getUTCSeconds() === 0 && dateTime.getUTCMilliseconds() === 0;
  return hasNoMinuteOrSmaller && DATE_ONLY_UTC_HOURS.includes(dateTime.getUTCHours());
}

/** 시각 > 날짜 (예: 2026.11.23) */
export function toAbsoluteDate(dateTime: Date): string {
  return `${dateTime.getFullYear()}.${toTwoDigits(dateTime.getMonth() + 1)}.${toTwoDigits(dateTime.getDate())}`;
}

/** 시각 > 절대 날짜·시각 (예: 2026.11.23 09:12) */
export function toAbsoluteDateTime(dateTime: Date): string {
  return `${toAbsoluteDate(dateTime)} ${toClockTime(dateTime)}`;
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

/** 지난 날 수 > 상대 표기 (날짜만 있는 값용, 기기 달력 날짜 기준): 같은 날·미래 오늘, 그 외 N일 전 */
export function toRelativeDay(date: Date, now: Date): string {
  const toDayStart = (dateTime: Date): number => new Date(dateTime.getFullYear(), dateTime.getMonth(), dateTime.getDate()).getTime();
  const elapsedDays = Math.round((toDayStart(now) - toDayStart(date)) / MILLISECONDS_PER_DAY);
  return elapsedDays < 1 ? '오늘' : `${elapsedDays}일 전`;
}

/** 시각 > 전체 표기 (수집 시각처럼 시각이 늘 있는 값): 절대 날짜·시각 · 상대 표기 (예: 2026.11.23 09:12 · 2시간 전) */
export function toDateTimeLabel(dateTime: Date, now: Date): string {
  return `${toAbsoluteDateTime(dateTime)} · ${toRelativeTime(dateTime, now)}`;
}

// 게시일 > 상대 표기: 날짜만 있는 값은 날 단위, 그 밖은 분·시간 단위
function toPublishedRelativeLabel(publishedAt: Date, now: Date): string {
  return isDateOnly(publishedAt) ? toRelativeDay(publishedAt, now) : toRelativeTime(publishedAt, now);
}

/**
 * 게시일 > 전체 표기 (상세 머리·긴급 타일·읽기 도우미): 절대 날짜·시각 · 상대 표기 (예: 2026.11.23 09:12 · 2시간 전)
 * 날짜만 있는 게시일은 시각 없이 (예: 2026.11.23 · 1일 전)
 */
export function toArticleDateLabel(publishedAt: Date, now: Date): string {
  if (isDateOnly(publishedAt)) return `${toAbsoluteDate(publishedAt)} · ${toRelativeDay(publishedAt, now)}`;
  return toDateTimeLabel(publishedAt, now);
}

// 상대 표기를 쓰는 한도 (그보다 오래되면 날짜로)
const RELATIVE_LABEL_MAXIMUM_DAYS = 7;

/** 게시일 > 짧은 표기 (카드·조밀한 행): 7일 안은 상대 표기(3시간 전), 그보다 오래되면 날짜(2026.11.01) */
export function toCompactDateLabel(publishedAt: Date, now: Date): string {
  const elapsedDays = (now.getTime() - publishedAt.getTime()) / MILLISECONDS_PER_DAY;
  if (elapsedDays < RELATIVE_LABEL_MAXIMUM_DAYS) return toPublishedRelativeLabel(publishedAt, now);
  return toAbsoluteDate(publishedAt);
}

/** 기기 시간대 그날 0시 (오늘 새 소식 기준, 서버 건수 응답이 없을 때) */
export function toStartOfLocalDay(dateTime: Date): Date {
  const startOfDay = new Date(dateTime.getTime());
  startOfDay.setHours(0, 0, 0, 0);
  return startOfDay;
}
