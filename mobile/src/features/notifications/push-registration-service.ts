import {
  AppFailure,
  PUSH_PERMISSION_DENIED_ERROR_CODE,
  PUSH_TOKEN_ERROR_CODE,
  PUSH_UNAVAILABLE_ERROR_CODE,
  QUIET_HOURS_WITHOUT_ALERT_ERROR_CODE,
} from '@/core/error/app-failure';
import type { AlertCategory } from '@/data/models/alert-category';
import type { QuietHours } from '@/data/models/quiet-hours';
import { readEnabledAlertCategories, saveEnabledAlertCategories } from '@/data/sources/alert-category-storage';
import {
  ensureUrgentAlertChannel,
  fetchExpoPushToken,
  getPushPlatform,
  getPushUnavailableReason,
  isPushPermissionGranted,
  requestPushPermission,
} from '@/data/sources/device-notification-client';
import { getDeviceTimeZone } from '@/data/sources/device-time-zone';
import { savePushDevice } from '@/data/sources/push-device-source';
import { readStoredQuietHours, saveStoredQuietHours } from '@/data/sources/quiet-hours-storage';

/**
 * 알림 종류 저장: 사용 가능 확인 > 채널 > 권한(켤 때만 요청) > 토큰 > 서버 PUT > 기기 저장
 * 실패는 AppFailure 로 던지고 기기 저장은 건드리지 않음 (서버와 기기 값이 어긋나지 않게)
 */
export async function saveAlertCategories(enabledAlertCategories: AlertCategory[]): Promise<AlertCategory[]> {
  if (getPushUnavailableReason() !== undefined) {
    throw new AppFailure(PUSH_UNAVAILABLE_ERROR_CODE, '이 기기에서는 알림을 받을 수 없어요.');
  }
  await ensureUrgentAlertChannel();
  // 모두 끄는 요청은 권한 없이도 보냄 (서버에서 기기를 지워야 함)
  if (enabledAlertCategories.length > 0) await ensurePushPermission();
  return (await registerPushDevice(enabledAlertCategories)).enabledAlertCategories;
}

/**
 * 방해 금지 시간 저장: 켠 알림 종류가 있을 때만 (서버 기기 행이 있어야 저장됨) > 서버 PUT > 기기 저장
 * 시간대는 지금 기기 시간대로 바꿔 보냄. 돌려주는 값은 서버가 저장한 값
 */
export async function saveQuietHours(quietHours: QuietHours): Promise<QuietHours> {
  if (getPushUnavailableReason() !== undefined) {
    throw new AppFailure(PUSH_UNAVAILABLE_ERROR_CODE, '이 기기에서는 알림을 받을 수 없어요.');
  }
  const enabledAlertCategories = await readEnabledAlertCategories();
  if (enabledAlertCategories.length === 0) {
    throw new AppFailure(QUIET_HOURS_WITHOUT_ALERT_ERROR_CODE, '알림 종류를 하나 이상 켠 뒤에 정해 주세요.');
  }
  await ensurePushPermission();
  return (await registerPushDevice(enabledAlertCategories, quietHours)).quietHours;
}

/**
 * 앱 시작 동기화: 기기에 켠 종류가 있을 때만 토큰을 다시 받아 서버에 등록 (토큰 교체 대응)
 * OS 권한이 꺼졌으면 기기 값을 모두 끔으로 맞추고 서버 기기도 지움. 돌려주는 값은 동기화 뒤의 켠 종류
 */
export async function syncPushRegistrationOnLaunch(): Promise<AlertCategory[]> {
  const storedAlertCategories = await readEnabledAlertCategories();
  if (storedAlertCategories.length === 0 || getPushUnavailableReason() !== undefined) return storedAlertCategories;
  if (!(await isPushPermissionGranted())) {
    await saveEnabledAlertCategories([]);
    // 서버 기기 삭제는 되는 만큼만 (권한 없이 토큰을 못 받는 기기는 서버가 무효 토큰으로 정리)
    await registerPushDevice([]).catch(() => undefined);
    return [];
  }
  await ensureUrgentAlertChannel();
  return (await registerPushDevice(storedAlertCategories)).enabledAlertCategories;
}

// 권한 확인 > 없으면 요청 > 그래도 없으면 거부 실패
async function ensurePushPermission(): Promise<void> {
  if (await isPushPermissionGranted()) return;
  if (await requestPushPermission()) return;
  throw new AppFailure(PUSH_PERMISSION_DENIED_ERROR_CODE, '기기 설정에서 알림을 허용해 주세요.');
}

// 토큰 발급 > 서버 PUT(방해 금지 시간은 늘 함께, 시간대는 지금 기기 값) > 서버가 돌려준 종류·방해 금지 시간을 기기에 저장
// 서버가 방해 금지 시간을 돌려주지 않으면(이전 서버) 보낸 값을 그대로 저장
async function registerPushDevice(
  enabledAlertCategories: AlertCategory[],
  requestedQuietHours?: QuietHours,
): Promise<{ enabledAlertCategories: AlertCategory[]; quietHours: QuietHours }> {
  const pushToken = await fetchExpoPushToken().catch(() => {
    throw new AppFailure(PUSH_TOKEN_ERROR_CODE, '알림을 준비하지 못했어요. 잠시 후 다시 시도해 주세요.');
  });
  const quietHours = { ...(requestedQuietHours ?? (await readStoredQuietHours())), timeZone: getDeviceTimeZone() };
  const savedSettings = await savePushDevice({ pushToken, platform: getPushPlatform(), enabledAlertCategories, quietHours });
  const savedQuietHours = savedSettings.quietHours ?? quietHours;
  await saveEnabledAlertCategories(savedSettings.enabledAlertCategories);
  await saveStoredQuietHours(savedQuietHours);
  return { enabledAlertCategories: savedSettings.enabledAlertCategories, quietHours: savedQuietHours };
}
