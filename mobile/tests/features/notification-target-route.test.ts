/**
 * 알림 누름 > 열 곳: 기사 1건, 공지, 묶음 알림, 방해 금지 시간 모아 보기, 이전 서버(targetTab), 깨진 data
 */
import { toNotificationInboxRoute } from '@/features/navigation/app-routes';
import { isTabRoute, toNotificationTarget, toNotificationTargetRoute } from '@/features/notifications/notification-target-route';

test('숫자 articleId 가 있으면 그 기사 상세 (알림 번호는 연 글로 기록)', () => {
  expect(toNotificationTarget({ notificationId: '7', notificationKind: 'ALERT', articleId: '123', targetTab: 'vulnerability' })).toEqual({
    route: '/articles/123',
    notificationId: 7,
  });
});
test('공지사항은 공지 상세', () => {
  expect(toNotificationTarget({ notificationId: '9', notificationKind: 'NOTICE', targetTab: 'today' })).toEqual({
    route: '/notices/9',
    notificationId: 9,
  });
});
test('묶음 알림(기사 번호 없음)은 알림 상세, 종류가 없어도 알림 상세', () => {
  expect(toNotificationTargetRoute({ notificationId: '12', notificationKind: 'ALERT', targetTab: 'vulnerability' })).toBe('/notifications/12');
  expect(toNotificationTargetRoute({ notificationId: '12' })).toBe('/notifications/12');
});
test('방해 금지 시간 모아 보기는 알림함', () => {
  expect(toNotificationTarget({ notificationKind: 'QUIET_HOURS_DIGEST', targetTab: 'vulnerability' })).toEqual({ route: '/notifications' });
});
test('이전 서버 data: 기사 번호면 상세, targetTab vulnerability 면 취약점 탭', () => {
  expect(toNotificationTargetRoute({ targetTab: 'vulnerability', articleId: '123' })).toBe('/articles/123');
  expect(toNotificationTargetRoute({ targetTab: 'vulnerability' })).toBe('/vulnerability');
});
test('숫자가 아닌 번호는 무시 (경로 조작 방지)', () => {
  expect(toNotificationTargetRoute({ targetTab: 'vulnerability', articleId: '../settings' })).toBe('/vulnerability');
  expect(toNotificationTargetRoute({ notificationId: '1/../../x', notificationKind: 'NOTICE' })).toBe('/');
  expect(toNotificationTargetRoute({ articleId: 123, notificationId: 5 })).toBe('/');
});
test.each([[{ targetTab: 'today' }], [{ targetTab: 'unknown' }], [{ notificationKind: 'WEATHER' }], [{}], [undefined], [null], ['vulnerability'], [42]])(
  '그 밖의 data %p 는 오늘 탭',
  (notificationData) => {
    expect(toNotificationTargetRoute(notificationData)).toBe('/');
  },
);
test('탭 경로만 탭으로 본다', () => {
  expect(isTabRoute('/')).toBe(true);
  expect(isTabRoute('/vulnerability')).toBe(true);
  expect(isTabRoute('/notifications')).toBe(false);
  expect(isTabRoute('/articles/1')).toBe(false);
});

test('찾지 못한 글의 알림함 보기는 그 종류 칸으로 (내려간 공지는 공지사항 칸)', () => {
  expect(toNotificationInboxRoute('NOTICE')).toBe('/notifications?segment=NOTICE');
  expect(toNotificationInboxRoute('ALERT')).toBe('/notifications?segment=ALERT');
});
