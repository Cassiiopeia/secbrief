/**
 * 알림함 상세 찾지 못함 안내: 종류별 문구 + 에러 코드 + 돌아갈 알림함 칸 (#119)
 */
import { RESOURCE_NOT_FOUND_ERROR_CODE } from '@/core/error/app-failure';
import { toNotificationDetailHeaderTitle, toNotificationNotFoundView } from '@/features/notifications/notification-detail-texts';
import { NOTIFICATION_NOT_FOUND_ERROR_CODE } from '@/features/notifications/use-app-notification-detail';

describe('알림함 상세 찾지 못함 안내', () => {
  test('없는 공지는 안내 + 에러 코드(기사 상세와 같은 RESOURCE_NOT_FOUND) + 공지사항 칸', () => {
    expect(toNotificationNotFoundView('NOTICE', NOTIFICATION_NOT_FOUND_ERROR_CODE)).toEqual({
      title: '내려간 공지사항이에요',
      description: '알림함에서 다른 공지사항을 볼 수 있어요.',
      errorCode: RESOURCE_NOT_FOUND_ERROR_CODE,
      inboxSegment: 'NOTICE',
    });
  });

  test('없는 알림·아침 요약도 에러 코드를 보여 주고 알림 칸으로 돌아가요', () => {
    for (const kind of ['ALERT', 'DIGEST'] as const) {
      const notFoundView = toNotificationNotFoundView(kind, NOTIFICATION_NOT_FOUND_ERROR_CODE);
      expect(notFoundView.errorCode).toBe('RESOURCE_NOT_FOUND');
      expect(notFoundView.inboxSegment).toBe('ALERT');
    }
    expect(toNotificationNotFoundView('ALERT', NOTIFICATION_NOT_FOUND_ERROR_CODE).title).toBe('보관 기간이 지난 알림이에요');
    expect(toNotificationNotFoundView('DIGEST', NOTIFICATION_NOT_FOUND_ERROR_CODE).title).toBe('보관 기간이 지난 요약이에요');
  });

  test('머리 제목', () => {
    expect(toNotificationDetailHeaderTitle('ALERT')).toBe('알림');
    expect(toNotificationDetailHeaderTitle('NOTICE')).toBe('공지사항');
    expect(toNotificationDetailHeaderTitle('DIGEST')).toBe('아침 요약');
  });
});
