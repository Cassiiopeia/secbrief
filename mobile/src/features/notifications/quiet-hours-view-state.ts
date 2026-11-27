import { toKoreanClockTime } from '@/core/format/notification-date-formatter';
import type { QuietHours } from '@/data/models/quiet-hours';

/** 바꿀 시각 2개: 시작, 끝 */
export type QuietHoursEdge = 'start' | 'end';

/** 방해 금지 시간 구역 표시 내용 */
export interface QuietHoursSectionView {
  isOn: boolean;
  /** 스위치 아래 설명: 켜면 구간, 끄면 무엇을 하는지 */
  switchDetail: string;
  /** 시작·끝 행 이름과 보일 시각 (켰을 때만 보임) */
  startLabel: string;
  endLabel: string;
  startTimeLabel: string;
  endTimeLabel: string;
  /** 시작·끝 선택기 읽기 도우미 문구 (행 이름 + 시각, 예: 방해 금지 시간 끝 (다음 날) 오전 8:00, 바꾸기) */
  startAccessibilityLabel: string;
  endAccessibilityLabel: string;
  /** 정할 수 없을 때 이유 한 줄 (알림 종류가 모두 꺼짐), 정할 수 있으면 없음 */
  unavailableHint?: string;
}

// 보류된 알림이 어떻게 되는지 한 줄 (켰을 때 시각 행 아래)
export const QUIET_HOURS_EXPLANATION = '이 시간에 온 긴급 알림은 모아 두었다가, 끝나면 한 번에 알려 드려요.';
// 시작과 끝이 같을 때 (서버 QUIET_HOURS_SAME_TIME 과 같은 규칙: 저장하지 않음)
export const QUIET_HOURS_SAME_TIME_MESSAGE = '시작과 끝을 다르게 정해 주세요.';
const QUIET_HOURS_OFF_DETAIL = '정한 시간에는 알림을 울리지 않아요';
const QUIET_HOURS_WITHOUT_ALERT_HINT = '알림 종류를 하나 이상 켜면 정할 수 있어요.';
const NEXT_DAY_LABEL = '다음 날';
export const QUIET_HOURS_TITLE = '방해 금지 시간';

/** 끝이 시작보다 이른 구간인지 (자정을 넘음, 예: 22:00 ~ 08:00). 같은 시각은 아님 */
export function isQuietHoursCrossingMidnight(quietHours: QuietHours): boolean {
  return quietHours.endTime < quietHours.startTime;
}

/** 시작과 끝이 같은지 (구간이 없어 저장하지 않음) */
export function isQuietHoursSameTime(quietHours: QuietHours): boolean {
  return quietHours.startTime === quietHours.endTime;
}

/** 구간 표기 (예: 오후 10:00 – 다음 날 오전 8:00, 오후 1:00 – 오후 3:00) */
export function toQuietHoursRangeLabel(quietHours: QuietHours): string {
  const endPrefix = isQuietHoursCrossingMidnight(quietHours) ? `${NEXT_DAY_LABEL} ` : '';
  return `${toKoreanClockTime(quietHours.startTime)} – ${endPrefix}${toKoreanClockTime(quietHours.endTime)}`;
}

/** 스위치를 바꾼 값 (켤 때 시각은 이전에 정한 값, 처음이면 기본 22:00 ~ 08:00) */
export function toToggledQuietHours(quietHours: QuietHours, isOn: boolean): QuietHours {
  return { ...quietHours, isEnabled: isOn };
}

/** 시각 하나를 바꾼 값 */
export function toQuietHoursWithTime(quietHours: QuietHours, edge: QuietHoursEdge, timeOfDay: string): QuietHours {
  return edge === 'start' ? { ...quietHours, startTime: timeOfDay } : { ...quietHours, endTime: timeOfDay };
}

/** 저장값 + 켠 알림 종류 > 구역 표시 내용 (알림 종류가 모두 꺼졌으면 꺼짐으로 보이고 이유 한 줄) */
export function toQuietHoursSectionView(quietHours: QuietHours, hasEnabledAlertCategory: boolean): QuietHoursSectionView {
  const isOn = quietHours.isEnabled && hasEnabledAlertCategory;
  const startLabel = '시작';
  const endLabel = isQuietHoursCrossingMidnight(quietHours) ? `끝 (${NEXT_DAY_LABEL})` : '끝';
  const startTimeLabel = toKoreanClockTime(quietHours.startTime);
  const endTimeLabel = toKoreanClockTime(quietHours.endTime);
  return {
    isOn,
    switchDetail: isOn ? toQuietHoursRangeLabel(quietHours) : QUIET_HOURS_OFF_DETAIL,
    startLabel,
    endLabel,
    startTimeLabel,
    endTimeLabel,
    startAccessibilityLabel: toTimePickerAccessibilityLabel(startLabel, startTimeLabel),
    endAccessibilityLabel: toTimePickerAccessibilityLabel(endLabel, endTimeLabel),
    unavailableHint: hasEnabledAlertCategory ? undefined : QUIET_HOURS_WITHOUT_ALERT_HINT,
  };
}

// 시각 선택기 읽기 도우미 문구: 구역 이름 + 행 이름 + 시각 + 할 일
function toTimePickerAccessibilityLabel(edgeLabel: string, timeLabel: string): string {
  return `${QUIET_HOURS_TITLE} ${edgeLabel} ${timeLabel}, 바꾸기`;
}

/** 방해 금지 시간 저장 확인 문구 (알림 막대): 켬·끔이 바뀌면 켰어요·껐어요, 시각만 바뀌면 바꿨어요 */
export function toQuietHoursSavedMessage(previousQuietHours: QuietHours, savedQuietHours: QuietHours): string {
  if (previousQuietHours.isEnabled !== savedQuietHours.isEnabled) {
    return savedQuietHours.isEnabled ? '방해 금지 시간을 켰어요' : '방해 금지 시간을 껐어요';
  }
  return '방해 금지 시간을 바꿨어요';
}
