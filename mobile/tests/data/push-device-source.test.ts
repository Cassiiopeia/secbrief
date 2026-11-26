/**
 * 푸시 기기 등록: PUT 본문 형태, 응답에서 아는 종류만, 기기 저장소 읽기 3경우 (fetch·저장소 가짜)
 */
import AsyncStorage from '@react-native-async-storage/async-storage';

import { apiConfig } from '@/core/config/api-config';
import { readEnabledAlertCategories, saveEnabledAlertCategories } from '@/data/sources/alert-category-storage';
import { savePushDevice } from '@/data/sources/push-device-source';

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

  const savedAlertCategories = await savePushDevice({
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
  // 앱이 모르는 종류는 버림
  expect(savedAlertCategories).toEqual(['KEV']);
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
