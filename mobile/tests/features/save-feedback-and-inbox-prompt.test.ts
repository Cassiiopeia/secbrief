/**
 * 저장 확인 문구(알림 막대)와 알림함 빈 상태의 알림 켜기 안내
 */
import { DEFAULT_QUIET_HOURS, type QuietHours } from '@/data/models/quiet-hours';
import { toNotificationInboxEnableAlertPrompt } from '@/features/notifications/notification-inbox-view';
import { toQuietHoursSavedMessage } from '@/features/notifications/quiet-hours-view-state';

const QUIET_OFF: QuietHours = { ...DEFAULT_QUIET_HOURS, isEnabled: false };
const QUIET_ON: QuietHours = { ...DEFAULT_QUIET_HOURS, isEnabled: true };

describe('방해 금지 시간 저장 확인 문구', () => {
  test('켬·끔이 바뀌면 켰어요·껐어요', () => {
    expect(toQuietHoursSavedMessage(QUIET_OFF, QUIET_ON)).toBe('방해 금지 시간을 켰어요');
    expect(toQuietHoursSavedMessage(QUIET_ON, QUIET_OFF)).toBe('방해 금지 시간을 껐어요');
  });

  test('시각만 바뀌면 바꿨어요', () => {
    expect(toQuietHoursSavedMessage(QUIET_ON, { ...QUIET_ON, startTime: '23:00' })).toBe('방해 금지 시간을 바꿨어요');
  });
});

describe('알림함 빈 상태의 알림 켜기 안내', () => {
  test('긴급 알림 칸 + 켠 종류 없음 + 알림 쓸 수 있는 기기면 안내', () => {
    expect(toNotificationInboxEnableAlertPrompt('ALERT', false, true)).toEqual({
      description: '알림을 켜면 긴급 보안 소식을 바로 알려 드려요.',
      actionLabel: '알림 켜러 가기',
    });
  });

  test('켠 종류가 있거나, 알림을 쓸 수 없는 기기거나, 공지사항 칸이면 안내 없음', () => {
    expect(toNotificationInboxEnableAlertPrompt('ALERT', true, true)).toBeUndefined();
    expect(toNotificationInboxEnableAlertPrompt('ALERT', false, false)).toBeUndefined();
    expect(toNotificationInboxEnableAlertPrompt('NOTICE', false, true)).toBeUndefined();
  });
});
