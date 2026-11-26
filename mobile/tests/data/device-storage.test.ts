/**
 * 기기 저장소 4종 동작 고정: 저장 키, 없음·깨진 값·읽기 실패 > 기본값, 저장 > JSON
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

import { readEnabledAlertCategories, saveEnabledAlertCategories } from '@/data/sources/alert-category-storage';
import { readNotificationReadState, saveNotificationReadState } from '@/data/sources/notification-read-storage';
import { readStoredQuietHours, saveStoredQuietHours } from '@/data/sources/quiet-hours-storage';
import { readReadArticleIds, saveReadArticleIds } from '@/data/sources/read-article-storage';
import { DEFAULT_QUIET_HOURS } from '@/data/models/quiet-hours';
import { EMPTY_NOTIFICATION_READ_STATE } from '@/data/models/app-notification-read-state';

jest.mock('@react-native-async-storage/async-storage', () =>
  jest.requireActual('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

// 저장소 4종: 키, 읽기·저장 함수, 기본값, 정상 저장값 예시
const STORAGE_CASES = [
  {
    key: 'secbrief.quietHours',
    read: readStoredQuietHours,
    save: (value: unknown) => saveStoredQuietHours(value as never),
    fallback: DEFAULT_QUIET_HOURS,
    stored: { isEnabled: true, startTime: '23:00', endTime: '07:00', timeZone: 'Asia/Tokyo' },
  },
  {
    key: 'secbrief.enabledAlertCategories',
    read: readEnabledAlertCategories,
    save: (value: unknown) => saveEnabledAlertCategories(value as never),
    fallback: [],
    stored: ['KEV', 'CRITICAL_CVE'],
  },
  {
    key: 'secbrief.notificationReadState',
    read: readNotificationReadState,
    save: (value: unknown) => saveNotificationReadState(value as never),
    fallback: EMPTY_NOTIFICATION_READ_STATE,
    stored: { seenUpToIdByKind: { ALERT: 42 }, openedNotificationIds: [40, 42] },
  },
  {
    key: 'secbrief.readArticleIds',
    read: readReadArticleIds,
    save: (value: unknown) => saveReadArticleIds(value as never),
    fallback: [],
    stored: ['1', '2'],
  },
];

beforeEach(async () => {
  await AsyncStorage.clear();
  jest.restoreAllMocks();
});

describe.each(STORAGE_CASES)('기기 저장소 $key', ({ key, read, save, fallback, stored }) => {
  test('저장값 없음 > 기본값', async () => {
    await expect(read()).resolves.toEqual(fallback);
  });

  test('깨진 JSON > 기본값', async () => {
    await AsyncStorage.setItem(key, '{broken');
    await expect(read()).resolves.toEqual(fallback);
  });

  test('읽기 실패 > 기본값', async () => {
    jest.spyOn(AsyncStorage, 'getItem').mockRejectedValueOnce(new Error('read failed'));
    await expect(read()).resolves.toEqual(fallback);
  });

  test('저장 > 같은 키에 JSON, 다시 읽으면 같은 값', async () => {
    await save(stored);
    expect(await AsyncStorage.getItem(key)).toBe(JSON.stringify(stored));
    await expect(read()).resolves.toEqual(stored);
  });
});

test('읽은 기사 ID: 배열이 아니면 빈 목록, 글자가 아닌 칸은 버림', async () => {
  await AsyncStorage.setItem('secbrief.readArticleIds', '{"id":1}');
  await expect(readReadArticleIds()).resolves.toEqual([]);
  await AsyncStorage.setItem('secbrief.readArticleIds', '["1",2,"3"]');
  await expect(readReadArticleIds()).resolves.toEqual(['1', '3']);
});

test('방해 금지 시간: 객체가 아니면 기본값', async () => {
  await AsyncStorage.setItem('secbrief.quietHours', '"text"');
  await expect(readStoredQuietHours()).resolves.toEqual(DEFAULT_QUIET_HOURS);
});
