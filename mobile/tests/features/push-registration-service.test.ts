/**
 * 푸시 등록 흐름: 채널 > 권한 > 토큰 > 서버 > 기기 저장, 실패 시 기기 저장 안 함, 앱 시작 동기화 4경우
 * (기기 알림·서버·저장소는 가짜)
 */
import { AppFailure } from '@/core/error/app-failure';
import { DEFAULT_ALERT_DIGEST_SETTINGS, EMPTY_ALERT_INTERESTS } from '@/data/models/alert-interest';
import * as alertCategoryStorage from '@/data/sources/alert-category-storage';
import * as alertInterestStorage from '@/data/sources/alert-interest-storage';
import * as deviceNotificationClient from '@/data/sources/device-notification-client';
import * as pushDeviceSource from '@/data/sources/push-device-source';
import * as quietHoursStorage from '@/data/sources/quiet-hours-storage';
import { DEFAULT_QUIET_HOURS, type QuietHours } from '@/data/models/quiet-hours';
import {
  saveAlertCategories,
  saveAlertInterestSettings,
  saveQuietHours,
  syncPushRegistrationOnLaunch,
} from '@/features/notifications/push-registration-service';

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
jest.mock('@/data/sources/quiet-hours-storage', () => ({ readStoredQuietHours: jest.fn(), saveStoredQuietHours: jest.fn() }));
jest.mock('@/data/sources/device-time-zone', () => ({ getDeviceTimeZone: () => 'Asia/Seoul' }));
jest.mock('@/data/sources/alert-interest-storage', () => ({
  readStoredAlertInterests: jest.fn(),
  saveStoredAlertInterests: jest.fn(),
  readStoredAlertDigestSettings: jest.fn(),
  saveStoredAlertDigestSettings: jest.fn(),
}));

const storage = jest.mocked(alertCategoryStorage);
const device = jest.mocked(deviceNotificationClient);
const server = jest.mocked(pushDeviceSource);
const quietHoursStore = jest.mocked(quietHoursStorage);
const interestStore = jest.mocked(alertInterestStorage);
// 관심 기술을 안 고른 기기가 늘 함께 보내는 값 (#93)
const DEFAULT_INTEREST_FIELDS = { interests: EMPTY_ALERT_INTERESTS, digest: DEFAULT_ALERT_DIGEST_SETTINGS };

const PUSH_TOKEN = 'ExponentPushToken[test]';

beforeEach(() => {
  jest.resetAllMocks();
  device.getPushUnavailableReason.mockReturnValue(undefined);
  device.getPushPlatform.mockReturnValue('ANDROID');
  device.isPushPermissionGranted.mockResolvedValue(true);
  device.fetchExpoPushToken.mockResolvedValue(PUSH_TOKEN);
  quietHoursStore.readStoredQuietHours.mockResolvedValue(DEFAULT_QUIET_HOURS);
  interestStore.readStoredAlertInterests.mockResolvedValue(EMPTY_ALERT_INTERESTS);
  interestStore.readStoredAlertDigestSettings.mockResolvedValue(DEFAULT_ALERT_DIGEST_SETTINGS);
  // 기본: 서버가 받은 값을 그대로 돌려줌
  server.savePushDevice.mockImplementation(async (registration) => ({
    enabledAlertCategories: registration.enabledAlertCategories,
    quietHours: registration.quietHours,
  }));
});

test('켜기: 채널 > 권한 요청 > 토큰 > 서버 > 기기 저장 순서', async () => {
  device.isPushPermissionGranted.mockResolvedValue(false);
  device.requestPushPermission.mockResolvedValue(true);

  await expect(saveAlertCategories(['KEV'])).resolves.toEqual(['KEV']);

  expect(server.savePushDevice).toHaveBeenCalledWith({ pushToken: PUSH_TOKEN, platform: 'ANDROID', enabledAlertCategories: ['KEV'], quietHours: DEFAULT_QUIET_HOURS, ...DEFAULT_INTEREST_FIELDS });
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
    quietHours: DEFAULT_QUIET_HOURS,
    ...DEFAULT_INTEREST_FIELDS,
  });
  expect(device.requestPushPermission).not.toHaveBeenCalled();
});

test('시작 동기화: 권한이 꺼졌으면 기기 값을 모두 끔으로 맞추고 서버 기기도 지운다', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue(['KEV']);
  device.isPushPermissionGranted.mockResolvedValue(false);
  server.savePushDevice.mockResolvedValue({ enabledAlertCategories: [] });
  await expect(syncPushRegistrationOnLaunch()).resolves.toEqual([]);
  expect(storage.saveEnabledAlertCategories).toHaveBeenCalledWith([]);
  expect(server.savePushDevice).toHaveBeenCalledWith({ pushToken: PUSH_TOKEN, platform: 'ANDROID', enabledAlertCategories: [], quietHours: DEFAULT_QUIET_HOURS, ...DEFAULT_INTEREST_FIELDS });
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

const NIGHT_QUIET_HOURS: QuietHours = { isEnabled: true, startTime: '23:00', endTime: '07:00', timeZone: 'Europe/London' };

test('방해 금지 시간 저장: 켠 종류와 함께 보내고, 시간대는 지금 기기 값, 서버 값을 기기에 저장', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue(['KEV']);

  await expect(saveQuietHours(NIGHT_QUIET_HOURS)).resolves.toEqual({ ...NIGHT_QUIET_HOURS, timeZone: 'Asia/Seoul' });

  expect(server.savePushDevice).toHaveBeenCalledWith({
    pushToken: PUSH_TOKEN,
    platform: 'ANDROID',
    enabledAlertCategories: ['KEV'],
    quietHours: { ...NIGHT_QUIET_HOURS, timeZone: 'Asia/Seoul' },
    ...DEFAULT_INTEREST_FIELDS,
  });
  expect(quietHoursStore.saveStoredQuietHours).toHaveBeenCalledWith({ ...NIGHT_QUIET_HOURS, timeZone: 'Asia/Seoul' });
});

test('방해 금지 시간 저장: 켠 알림 종류가 없으면 APP_QUIET_HOURS_WITHOUT_ALERT, 서버 호출 없음', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue([]);
  await expect(saveQuietHours(NIGHT_QUIET_HOURS)).rejects.toMatchObject({ errorCode: 'APP_QUIET_HOURS_WITHOUT_ALERT' });
  expect(server.savePushDevice).not.toHaveBeenCalled();
});

test('방해 금지 시간을 모르는 이전 서버면 보낸 값을 기기에 저장한다', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue(['KEV']);
  server.savePushDevice.mockResolvedValue({ enabledAlertCategories: ['KEV'] });
  await saveQuietHours(NIGHT_QUIET_HOURS);
  expect(quietHoursStore.saveStoredQuietHours).toHaveBeenCalledWith({ ...NIGHT_QUIET_HOURS, timeZone: 'Asia/Seoul' });
});

test('방해 금지 시간 저장 서버 실패는 그 코드 그대로, 기기 저장 없음', async () => {
  storage.readEnabledAlertCategories.mockResolvedValue(['KEV']);
  server.savePushDevice.mockRejectedValue(new AppFailure('QUIET_HOURS_SAME_TIME', '시작과 끝을 다르게 정해 주세요.'));
  await expect(saveQuietHours(NIGHT_QUIET_HOURS)).rejects.toMatchObject({ errorCode: 'QUIET_HOURS_SAME_TIME' });
  expect(quietHoursStore.saveStoredQuietHours).not.toHaveBeenCalled();
});

test('알림 종류를 바꿀 때 기기에 저장된 방해 금지 시간도 함께 보낸다 (서버 기기가 새로 생겨도 유지)', async () => {
  quietHoursStore.readStoredQuietHours.mockResolvedValue(NIGHT_QUIET_HOURS);
  await saveAlertCategories(['KEV']);
  expect(server.savePushDevice).toHaveBeenCalledWith(
    expect.objectContaining({ quietHours: { ...NIGHT_QUIET_HOURS, timeZone: 'Asia/Seoul' } }),
  );
});

describe('관심 기술 (#93)', () => {
  const interests = { productCodes: ['product-a'], fieldCodes: [], ecosystemCodes: ['eco-a'] };
  const digest = { deliveryMode: 'IMMEDIATE' as const, digestTime: '07:30' };

  test('관심 기술 저장: 기기 저장 > 권한 > 서버에 관심 기술·받는 방식을 보내고 서버가 남긴 code 로 기기 저장', async () => {
    storage.readEnabledAlertCategories.mockResolvedValue([]);
    interestStore.readStoredAlertInterests.mockResolvedValue(interests);
    interestStore.readStoredAlertDigestSettings.mockResolvedValue(digest);
    // 서버가 지워진 code 를 빼고 돌려줌
    server.savePushDevice.mockImplementation(async (registration) => ({
      enabledAlertCategories: registration.enabledAlertCategories,
      interests: { ...interests, productCodes: [] },
      digest,
    }));

    const savedSettings = await saveAlertInterestSettings(interests, digest);

    expect(interestStore.saveStoredAlertInterests).toHaveBeenNthCalledWith(1, interests);
    expect(server.savePushDevice).toHaveBeenCalledWith(expect.objectContaining({ enabledAlertCategories: [], interests, digest }));
    expect(savedSettings).toEqual({ interests: { ...interests, productCodes: [] }, digest, isSentToServer: true });
    expect(interestStore.saveStoredAlertInterests).toHaveBeenLastCalledWith({ ...interests, productCodes: [] });
  });

  test('알림을 못 쓰는 기기(웹)는 기기에만 저장하고 서버를 부르지 않는다', async () => {
    device.getPushUnavailableReason.mockReturnValue('WEB');
    await expect(saveAlertInterestSettings(interests, digest)).resolves.toEqual({ interests, digest, isSentToServer: false });
    expect(server.savePushDevice).not.toHaveBeenCalled();
  });

  test('시작 동기화: 켠 종류가 없어도 관심 기술이 있으면 다시 등록한다', async () => {
    storage.readEnabledAlertCategories.mockResolvedValue([]);
    interestStore.readStoredAlertInterests.mockResolvedValue(interests);
    await syncPushRegistrationOnLaunch();
    expect(server.savePushDevice).toHaveBeenCalledWith(expect.objectContaining({ enabledAlertCategories: [], interests }));
  });
});
