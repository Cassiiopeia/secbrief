/**
 * 저장 확인 문구(알림 막대)와 알림함 빈 상태의 알림 켜기 안내
 */
import { DEFAULT_ALERT_DIGEST_SETTINGS } from '@/data/models/alert-interest';
import { DEFAULT_QUIET_HOURS, type QuietHours } from '@/data/models/quiet-hours';
import { toAlertInterestSavedMessage } from '@/features/interests/alert-interest-saved-messages';
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

describe('관심 기술·아침 요약 저장 확인 문구 (#119 B1)', () => {
  const DIGEST_ON = { ...DEFAULT_ALERT_DIGEST_SETTINGS, deliveryMode: 'DIGEST' as const };
  const DIGEST_OFF = { ...DEFAULT_ALERT_DIGEST_SETTINGS, deliveryMode: 'IMMEDIATE' as const };

  test('관심 기술: 서버까지 보내면 저장했어요, 기기에만이면 앱 안내', () => {
    expect(toAlertInterestSavedMessage('interests', DIGEST_ON, true)).toBe('관심 기술을 저장했어요');
    expect(toAlertInterestSavedMessage('interests', DIGEST_ON, false)).toBe('관심 기술을 이 기기에 저장했어요. 알림은 앱에서 받을 수 있어요');
  });

  test('아침 요약 스위치: 저장된 방식으로 켰어요·껐어요 (관심 기술 문구 아님)', () => {
    expect(toAlertInterestSavedMessage('digestDeliveryMode', DIGEST_ON, true)).toBe('아침 요약을 켰어요');
    expect(toAlertInterestSavedMessage('digestDeliveryMode', DIGEST_OFF, true)).toBe('아침 요약을 껐어요');
    expect(toAlertInterestSavedMessage('digestDeliveryMode', DIGEST_OFF, false)).toBe('아침 요약을 껐어요');
  });

  test('받는 시각: 바꿨어요', () => {
    expect(toAlertInterestSavedMessage('digestTime', { ...DIGEST_ON, digestTime: '09:00' }, true)).toBe('아침 요약 받는 시각을 바꿨어요');
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
