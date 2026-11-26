/**
 * 푸시 등록 흐름: 채널 > 권한 > 토큰 > 서버 > 기기 저장, 실패 시 기기 저장 안 함, 앱 시작 동기화 4경우
 * (기기 알림·서버·저장소는 가짜)
 */
import { AppFailure } from '@/core/error/app-failure';
import * as alertCategoryStorage from '@/data/sources/alert-category-storage';
import * as deviceNotificationClient from '@/data/sources/device-notification-client';
import * as pushDeviceSource from '@/data/sources/push-device-source';
import { saveAlertCategories, syncPushRegistrationOnLaunch } from '@/features/notifications/push-registration-service';

// 네이티브 모듈(저장소, 알림)을 불러오지 않게 가짜를 직접 만듦
jest.mock('@/data/sources/alert-category-storage', () => ({
  readEnabledAlertCategories: jest.fn(),
  saveEnabledAlertCategories: jest.fn(),
}));
jest.mock('@/data/sources/device-notification-client', () => ({
  ensureUrgentAlertChannel: jest.fn(),
  fetchExpoPushToken: jest.fn(),
  getPushPlatform: jest.fn(),
  getPushUnavailableReason: jest.fn(),
  isPushPermissionGranted: jest.fn(),
  requestPushPermission: jest.fn(),
}));
jest.mock('@/data/sources/push-device-source', () => ({ savePushDevice: jest.fn() }));

const storage = jest.mocked(alertCategoryStorage);
const device = jest.mocked(deviceNotificationClient);
const server = jest.mocked(pushDeviceSource);

const PUSH_TOKEN = 'ExponentPushToken[test]';

beforeEach(() => {
  jest.resetAllMocks();
  device.getPushUnavailableReason.mockReturnValue(undefined);
  device.getPushPlatform.mockReturnValue('ANDROID');
  device.isPushPermissionGranted.mockResolvedValue(true);
  device.fetchExpoPushToken.mockResolvedValue(PUSH_TOKEN);
  server.savePushDevice.mockImplementation(async (registration) => registration.enabledAlertCategories);
});

test('켜기: 채널 > 권한 요청 > 토큰 > 서버 > 기기 저장 순서', async () => {
  device.isPushPermissionGranted.mockResolvedValue(false);
  device.requestPushPermission.mockResolvedValue(true);

  await expect(saveAlertCategories(['KEV'])).resolves.toEqual(['KEV']);

  expect(server.savePushDevice).toHaveBeenCalledWith({ pushToken: PUSH_TOKEN, platform: 'ANDROID', enabledAlertCategories: ['KEV'] });
  expect(storage.saveEnabledAlertCategories).toHaveBeenCalledWith(['KEV']);
  // Android 13 은 채널이 있어야 권한 창이 뜸
  expect(device.ensureUrgentAlertChannel.mock.invocationCallOrder[0]).toBeLessThan(
    device.requestPushPermission.mock.invocationCallOrder[0],
  );
});

test('권한을 거부하면 APP_PUSH_PERMISSION_DENIED, 서버·기기 저장 없음', async () => {
  device.isPushPermissionGranted.mockResolvedValue(false);
  device.requestPushPermission.mockResolvedValue(false);

  await expect(saveAlertCategories(['KEV'])).rejects.toMatchObject({ errorCode: 'APP_PUSH_PERMISSION_DENIED' });
  expect(server.savePushDevice).not.toHaveBeenCalled();
  expect(storage.saveEnabledAlertCategories).not.toHaveBeenCalled();
});

test('토큰을 못 받으면 APP_PUSH_TOKEN', async () => {
  device.fetchExpoPushToken.mockRejectedValue(new Error('offline'));
  await expect(saveAlertCategories(['KEV'])).rejects.toMatchObject({ errorCode: 'APP_PUSH_TOKEN' });
  expect(storage.saveEnabledAlertCategories).not.toHaveBeenCalled();
});

test('서버가 실패하면 그 에러 코드 그대로, 기기 저장 없음', async () => {
  server.savePushDevice.mockRejectedValue(new AppFailure('VALIDATION_ERROR', '요청 값을 확인해 주세요.'));
  await expect(saveAlertCategories(['KEV'])).rejects.toMatchObject({ errorCode: 'VALIDATION_ERROR' });
  expect(storage.saveEnabledAlertCategories).not.toHaveBeenCalled();
});

test('모두 끄기는 권한을 묻지 않고 빈 목록을 서버에 보낸다', async () => {
  device.isPushPermissionGranted.mockResolvedValue(false);

  await expect(saveAlertCategories([])).resolves.toEqual([]);

  expect(device.requestPushPermission).not.toHaveBeenCalled();
  expect(server.savePushDevice).toHaveBeenCalledWith(expect.objectContaining({ enabledAlertCategories: [] }));
  expect(storage.saveEnabledAlertCategories).toHaveBeenCalledWith([]);
});

test('쓸 수 없는 환경에서는 APP_PUSH_UNAVAILABLE', async () => {
  device.getPushUnavailableReason.mockReturnValue('NO_PROJECT_ID');
  await expect(saveAlertCategories(['KEV'])).rejects.toMatchObject({ errorCode: 'APP_PUSH_UNAVAILABLE' });
  expect(device.fetchExpoPushToken).not.toHaveBeenCalled();
});

test('시작 동기화: 켠 종류가 없으면 아무것도 하지 않는다', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue([]);
  await expect(syncPushRegistrationOnLaunch()).resolves.toEqual([]);
  expect(device.fetchExpoPushToken).not.toHaveBeenCalled();
});

test('시작 동기화: 권한이 허용이면 토큰을 다시 받아 서버에 등록한다', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue(['KEV', 'CRITICAL_CVE']);
  await expect(syncPushRegistrationOnLaunch()).resolves.toEqual(['KEV', 'CRITICAL_CVE']);
  expect(server.savePushDevice).toHaveBeenCalledWith({
    pushToken: PUSH_TOKEN,
    platform: 'ANDROID',
    enabledAlertCategories: ['KEV', 'CRITICAL_CVE'],
  });
  expect(device.requestPushPermission).not.toHaveBeenCalled();
});

test('시작 동기화: 권한이 꺼졌으면 기기 값을 모두 끔으로 맞추고 서버 기기도 지운다', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue(['KEV']);
  device.isPushPermissionGranted.mockResolvedValue(false);
  server.savePushDevice.mockResolvedValue([]);
  await expect(syncPushRegistrationOnLaunch()).resolves.toEqual([]);
  expect(storage.saveEnabledAlertCategories).toHaveBeenCalledWith([]);
  expect(server.savePushDevice).toHaveBeenCalledWith({ pushToken: PUSH_TOKEN, platform: 'ANDROID', enabledAlertCategories: [] });
});

test('시작 동기화: 권한이 꺼진 기기가 토큰을 못 받아도 끔으로 맞춘다', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue(['KEV']);
  device.isPushPermissionGranted.mockResolvedValue(false);
  device.fetchExpoPushToken.mockRejectedValue(new Error('no permission'));
  await expect(syncPushRegistrationOnLaunch()).resolves.toEqual([]);
  expect(server.savePushDevice).not.toHaveBeenCalled();
});

test('시작 동기화: 서버 실패는 던지고 기기 값은 그대로 둔다 (부르는 쪽이 조용히 넘김)', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue(['KEV']);
  server.savePushDevice.mockRejectedValue(new AppFailure('APP_NETWORK', '인터넷 연결을 확인해 주세요.'));
  await expect(syncPushRegistrationOnLaunch()).rejects.toMatchObject({ errorCode: 'APP_NETWORK' });
  expect(storage.saveEnabledAlertCategories).not.toHaveBeenCalled();
});
