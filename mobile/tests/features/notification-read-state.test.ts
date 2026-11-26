/**
 * 알림함 읽음 기록: 연 글 더하기(한도), 본 번호(작아지지 않음), 종 점, 새 글 판정 (처음 방문 24시간), 저장값 읽기
 */
import type { AppNotificationSummary } from '@/data/models/app-notification';
import { EMPTY_NOTIFICATION_READ_STATE, toNotificationReadState } from '@/data/models/app-notification-read-state';
import {
  OPENED_NOTIFICATION_MAXIMUM_COUNT,
  hasUnseenNotification,
  isNotificationNew,
  toReadStateWithOpened,
  toReadStateWithSeen,
} from '@/features/notifications/notification-read-state';

const NOW = new Date('2026-11-27T12:00:00+09:00');

function toSummary(id: number, createdAt: string): AppNotificationSummary {
  return {
    id,
    kind: 'ALERT',
    alertCategories: ['KEV'],
    alertCategory: 'KEV',
    title: '악용 중인 취약점이 확인됐어요',
    body: '',
    articleCount: 1,
    isPinned: false,
    createdAt: new Date(createdAt),
    updatedAt: new Date(createdAt),
  };
}

test('연 글은 맨 뒤로 옮기고 한도를 넘으면 오래된 것부터 버린다', () => {
  const opened = toReadStateWithOpened({ ...EMPTY_NOTIFICATION_READ_STATE, openedNotificationIds: [1, 2, 3] }, 2);
  expect(opened.openedNotificationIds).toEqual([1, 3, 2]);
  const fullIds = Array.from({ length: OPENED_NOTIFICATION_MAXIMUM_COUNT }, (_, index) => index);
  const capped = toReadStateWithOpened({ ...EMPTY_NOTIFICATION_READ_STATE, openedNotificationIds: fullIds }, 9999);
  expect(capped.openedNotificationIds).toHaveLength(OPENED_NOTIFICATION_MAXIMUM_COUNT);
  expect(capped.openedNotificationIds[0]).toBe(1);
  expect(capped.openedNotificationIds.at(-1)).toBe(9999);
});

test('본 번호는 가장 큰 값으로만 바뀌고, 바뀌지 않으면 같은 기록을 돌려준다', () => {
  const seen = toReadStateWithSeen(EMPTY_NOTIFICATION_READ_STATE, 'ALERT', [5, 9, 7]);
  expect(seen.seenUpToIdByKind).toEqual({ ALERT: 9 });
  expect(toReadStateWithSeen(seen, 'ALERT', [3])).toBe(seen);
  expect(toReadStateWithSeen(seen, 'NOTICE', [2]).seenUpToIdByKind).toEqual({ ALERT: 9, NOTICE: 2 });
  expect(toReadStateWithSeen(seen, 'ALERT', [])).toBe(seen);
});

test('종 점: 최근 번호가 본 번호보다 클 때, 본 기록이 없으면 글이 있을 때, 글이 없으면 없음', () => {
  expect(hasUnseenNotification(10, 9)).toBe(true);
  expect(hasUnseenNotification(9, 9)).toBe(false);
  expect(hasUnseenNotification(3, undefined)).toBe(true);
  expect(hasUnseenNotification(undefined, undefined)).toBe(false);
});

test('새 글: 본 번호보다 큰 글, 연 글은 아님', () => {
  const baseline = { seenUpToId: 10, openedNotificationIds: new Set([12]) };
  expect(isNotificationNew(toSummary(11, '2026-11-20T00:00:00+09:00'), baseline, NOW)).toBe(true);
  expect(isNotificationNew(toSummary(10, '2026-11-27T11:00:00+09:00'), baseline, NOW)).toBe(false);
  expect(isNotificationNew(toSummary(12, '2026-11-27T11:00:00+09:00'), baseline, NOW)).toBe(false);
});

test('처음 방문(본 기록 없음)은 최근 24시간 글만 새 글', () => {
  const baseline = { seenUpToId: undefined, openedNotificationIds: new Set<number>() };
  expect(isNotificationNew(toSummary(1, '2026-11-26T12:30:00+09:00'), baseline, NOW)).toBe(true);
  expect(isNotificationNew(toSummary(2, '2026-11-26T11:30:00+09:00'), baseline, NOW)).toBe(false);
});

test('저장값 읽기: 모르는 종류·숫자 아닌 번호를 버린다', () => {
  expect(
    toNotificationReadState({ seenUpToIdByKind: { ALERT: 4, NOTICE: '7', WEATHER: 3 }, openedNotificationIds: [1, 'x', 2.5, 3] }),
  ).toEqual({ seenUpToIdByKind: { ALERT: 4 }, openedNotificationIds: [1, 3] });
  expect(toNotificationReadState('broken')).toEqual(EMPTY_NOTIFICATION_READ_STATE);
  expect(toNotificationReadState({})).toEqual(EMPTY_NOTIFICATION_READ_STATE);
});
