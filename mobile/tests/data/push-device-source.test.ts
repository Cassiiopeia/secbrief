/**
 * 푸시 기기 등록: PUT 본문 형태(방해 금지 시간 포함), 응답에서 아는 종류만, 기기 저장소 읽기 (fetch·저장소 가짜)
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiConfig } from '@/core/config/api-config';
import { readEnabledAlertCategories, saveEnabledAlertCategories } from '@/data/sources/alert-category-storage';
import { DEFAULT_QUIET_HOURS } from '@/data/models/quiet-hours';
import { savePushDevice } from '@/data/sources/push-device-source';
import { readStoredQuietHours, saveStoredQuietHours } from '@/data/sources/quiet-hours-storage';

jest.mock('@react-native-async-storage/async-storage', () =>
  jest.requireActual('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

const fetchMock = jest.fn();

beforeEach(async () => {
  fetchMock.mockReset();
  global.fetch = fetchMock as unknown as typeof fetch;
  await AsyncStorage.clear();
});

test('PUT /api/v1/push-devices 로 토큰·기기 종류·켠 종류를 보낸다', async () => {
  fetchMock.mockResolvedValueOnce({
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ data: { enabledAlertCategories: ['KEV', 'NEW_KIND'] } }),
  });

  const savedSettings = await savePushDevice({
    pushToken: 'ExponentPushToken[test]',
    platform: 'IOS',
    enabledAlertCategories: ['KEV'],
  });

  const [requestUrl, requestInit] = fetchMock.mock.calls[0];
  expect(requestUrl).toBe(`${apiConfig.baseUrl}/api/v1/push-devices`);
  expect(requestInit.method).toBe('PUT');
  expect(requestInit.headers['Content-Type']).toBe('application/json');
  expect(JSON.parse(requestInit.body)).toEqual({
    pushToken: 'ExponentPushToken[test]',
    platform: 'IOS',
    enabledAlertCategories: ['KEV'],
  });
  // 앱이 모르는 종류는 버림, 방해 금지 시간을 주지 않는 이전 서버면 없음
  expect(savedSettings).toEqual({ enabledAlertCategories: ['KEV'], quietHours: undefined });
});

test('서버 검증 실패는 errorCode 그대로', async () => {
  fetchMock.mockResolvedValueOnce({
    ok: false,
    status: 400,
    text: async () => JSON.stringify({ errorCode: 'VALIDATION_ERROR', message: '요청 값을 확인해 주세요.' }),
  });
  await expect(
    savePushDevice({ pushToken: '', platform: 'ANDROID', enabledAlertCategories: [] }),
  ).rejects.toMatchObject({ errorCode: 'VALIDATION_ERROR', userMessage: '요청 값을 확인해 주세요.' });
});

test('기기 저장소: 저장한 종류를 그대로 읽는다', async () => {
  await saveEnabledAlertCategories(['KISA_URGENT', 'KEV']);
  await expect(readEnabledAlertCategories()).resolves.toEqual(['KISA_URGENT', 'KEV']);
});
test('기기 저장소: 저장한 적이 없으면 빈 목록', async () => {
  await expect(readEnabledAlertCategories()).resolves.toEqual([]);
});
test('기기 저장소: 깨진 값은 빈 목록', async () => {
  await AsyncStorage.setItem('secbrief.enabledAlertCategories', '{not json');
  await expect(readEnabledAlertCategories()).resolves.toEqual([]);
});

test('방해 금지 시간을 함께 보내고, 서버가 돌려준 값을 형식에 맞춰 읽는다', async () => {
  fetchMock.mockResolvedValueOnce({
    ok: true,
    status: 200,
    text: async () =>
      JSON.stringify({
        data: {
          enabledAlertCategories: ['KEV'],
          quietHours: { isEnabled: true, startTime: '23:30', endTime: '7:00', timeZone: 'Asia/Seoul' },
        },
      }),
  });
  const quietHours = { isEnabled: true, startTime: '23:30', endTime: '07:00', timeZone: 'Asia/Seoul' };

  const savedSettings = await savePushDevice({ pushToken: 'ExponentPushToken[test]', platform: 'ANDROID', enabledAlertCategories: ['KEV'], quietHours });

  expect(JSON.parse(fetchMock.mock.calls[0][1].body).quietHours).toEqual(quietHours);
  // 형식이 틀린 칸(7:00)만 기본값
  expect(savedSettings.quietHours).toEqual({ isEnabled: true, startTime: '23:30', endTime: '08:00', timeZone: 'Asia/Seoul' });
});

test('방해 금지 시간 저장소: 없으면 꺼진 기본값, 저장한 값은 그대로, 깨진 값은 기본값', async () => {
  await expect(readStoredQuietHours()).resolves.toEqual(DEFAULT_QUIET_HOURS);
  const quietHours = { isEnabled: true, startTime: '22:30', endTime: '06:00', timeZone: 'America/New_York' };
  await saveStoredQuietHours(quietHours);
  await expect(readStoredQuietHours()).resolves.toEqual(quietHours);
  await AsyncStorage.setItem('secbrief.quietHours', 'not json');
  await expect(readStoredQuietHours()).resolves.toEqual(DEFAULT_QUIET_HOURS);
});
