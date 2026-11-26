/**
 * 방해 금지 시간 (기기별, 서버가 발송 때 적용): 켬 여부, 시작·끝 시각("HH:mm", 기기 시간대 기준), 시간대(IANA)
 * 끝이 시작보다 이르면 자정을 넘는 구간 (예: 22:00 ~ 다음 날 08:00)
 */
export interface QuietHours {
  isEnabled: boolean;
  startTime: string;
  endTime: string;
  timeZone: string;
}

// 시간대를 모를 때 쓰는 값 (서버 기본값과 같음)
export const DEFAULT_TIME_ZONE = 'Asia/Seoul';

/** 기본값: 꺼짐 + 켰을 때 먼저 채워 둘 22:00 ~ 08:00 (서버 기본값과 같음) */
export const DEFAULT_QUIET_HOURS: QuietHours = {
  isEnabled: false,
  startTime: '22:00',
  endTime: '08:00',
  timeZone: DEFAULT_TIME_ZONE,
};

// "HH:mm" 모양 (00:00 ~ 23:59)
const TIME_OF_DAY_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

/** 시각 글자가 "HH:mm" 모양인지 */
export function isTimeOfDay(value: unknown): value is string {
  return typeof value === 'string' && TIME_OF_DAY_PATTERN.test(value);
}

/** 임의의 값(서버 응답·저장값) > 방해 금지 시간 (형식이 틀린 칸만 기본값, 객체가 아니면 없음) */
export function toQuietHours(value: unknown): QuietHours | undefined {
  if (typeof value !== 'object' || value === null) return undefined;
  const { isEnabled, startTime, endTime, timeZone } = value as Record<string, unknown>;
  return {
    isEnabled: isEnabled === true,
    startTime: isTimeOfDay(startTime) ? startTime : DEFAULT_QUIET_HOURS.startTime,
    endTime: isTimeOfDay(endTime) ? endTime : DEFAULT_QUIET_HOURS.endTime,
    timeZone: typeof timeZone === 'string' && timeZone.length > 0 ? timeZone : DEFAULT_TIME_ZONE,
  };
}
