/**
 * 방해 금지 시간 뷰 상태: 자정 넘는 구간, 같은 시각, 구간 표기, 켜기·시각 바꾸기, 알림 종류가 없을 때
 */
import { DEFAULT_QUIET_HOURS, type QuietHours, toQuietHours } from '@/data/models/quiet-hours';
import { toKoreanClockTime } from '@/core/format/notification-date-formatter';
import {
  isQuietHoursCrossingMidnight,
  isQuietHoursSameTime,
  toQuietHoursRangeLabel,
  toQuietHoursSectionView,
  toQuietHoursWithTime,
  toToggledQuietHours,
} from '@/features/notifications/quiet-hours-view-state';

const NIGHT: QuietHours = { ...DEFAULT_QUIET_HOURS, isEnabled: true };
const AFTERNOON: QuietHours = { ...NIGHT, startTime: '13:00', endTime: '15:30' };

test('오전·오후 표기 (자정 오전 12시, 정오 오후 12시)', () => {
  expect(toKoreanClockTime('22:00')).toBe('오후 10:00');
  expect(toKoreanClockTime('08:05')).toBe('오전 8:05');
  expect(toKoreanClockTime('00:30')).toBe('오전 12:30');
  expect(toKoreanClockTime('12:00')).toBe('오후 12:00');
});

test('끝이 시작보다 이르면 자정을 넘는 구간 (다음 날 표기)', () => {
  expect(isQuietHoursCrossingMidnight(NIGHT)).toBe(true);
  expect(toQuietHoursRangeLabel(NIGHT)).toBe('오후 10:00 – 다음 날 오전 8:00');
  expect(isQuietHoursCrossingMidnight(AFTERNOON)).toBe(false);
  expect(toQuietHoursRangeLabel(AFTERNOON)).toBe('오후 1:00 – 오후 3:30');
});

test('시작과 끝이 같으면 같은 시각 (자정을 넘는 구간 아님)', () => {
  const sameTime = { ...NIGHT, endTime: '22:00' };
  expect(isQuietHoursSameTime(sameTime)).toBe(true);
  expect(isQuietHoursCrossingMidnight(sameTime)).toBe(false);
});

test('켤 때 이전에 정한 시각을 쓰고, 처음이면 22:00 ~ 08:00', () => {
  expect(toToggledQuietHours(DEFAULT_QUIET_HOURS, true)).toEqual({ ...DEFAULT_QUIET_HOURS, isEnabled: true });
  expect(toToggledQuietHours({ ...AFTERNOON, isEnabled: false }, true)).toEqual(AFTERNOON);
});

test('시각 하나만 바꾼다', () => {
  expect(toQuietHoursWithTime(NIGHT, 'start', '23:00')).toEqual({ ...NIGHT, startTime: '23:00' });
  expect(toQuietHoursWithTime(NIGHT, 'end', '07:00')).toEqual({ ...NIGHT, endTime: '07:00' });
});

test('구역 표시: 켜면 구간, 끝 행에 다음 날, 끄면 무엇을 하는지', () => {
  expect(toQuietHoursSectionView(NIGHT, true)).toEqual({
    isOn: true,
    switchDetail: '오후 10:00 – 다음 날 오전 8:00',
    startLabel: '시작',
    endLabel: '끝 (다음 날)',
    startTimeLabel: '오후 10:00',
    endTimeLabel: '오전 8:00',
    startAccessibilityLabel: '방해 금지 시간 시작 오후 10:00, 바꾸기',
    endAccessibilityLabel: '방해 금지 시간 끝 (다음 날) 오전 8:00, 바꾸기',
    unavailableHint: undefined,
  });
  expect(toQuietHoursSectionView(AFTERNOON, true).endLabel).toBe('끝');
  expect(toQuietHoursSectionView(DEFAULT_QUIET_HOURS, true).switchDetail).toBe('정한 시간에는 알림을 울리지 않아요');
});

test('시작·끝 선택기 읽기 도우미 문구에 시각 글자가 들어가요 (#119)', () => {
  const sectionView = toQuietHoursSectionView(AFTERNOON, true);
  expect(sectionView.startAccessibilityLabel).toBe('방해 금지 시간 시작 오후 1:00, 바꾸기');
  expect(sectionView.endAccessibilityLabel).toBe('방해 금지 시간 끝 오후 3:30, 바꾸기');
});

test('알림 종류가 모두 꺼지면 켠 값이어도 꺼짐으로 보이고 이유 한 줄', () => {
  const sectionView = toQuietHoursSectionView(NIGHT, false);
  expect(sectionView.isOn).toBe(false);
  expect(sectionView.unavailableHint).toBe('알림 종류를 하나 이상 켜면 정할 수 있어요.');
});

test('서버·저장값 읽기: 형식이 틀린 칸만 기본값, 객체가 아니면 없음', () => {
  expect(toQuietHours({ isEnabled: 'yes', startTime: '25:00', endTime: '06:00' })).toEqual({
    isEnabled: false,
    startTime: '22:00',
    endTime: '06:00',
    timeZone: 'Asia/Seoul',
  });
  expect(toQuietHours(null)).toBeUndefined();
  expect(toQuietHours('22:00')).toBeUndefined();
});
