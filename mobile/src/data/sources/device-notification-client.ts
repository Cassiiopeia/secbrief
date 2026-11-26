import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
// enum 은 이름으로 import (Notifications.X 접근은 eslint import/namespace 가 찾지 못함)
import { AndroidImportance, IosAuthorizationStatus } from 'expo-notifications';
import { Platform } from 'react-native';

import { pushConfig } from '@/core/config/push-config';

import type { PushPlatform } from '../models/alert-category';

/** 알림을 쓸 수 없는 이유 3개: 웹, 실기기 아님, Expo 프로젝트 ID 없음 */
export type PushUnavailableReason = 'WEB' | 'NOT_PHYSICAL_DEVICE' | 'NO_PROJECT_ID';

/**
 * 기기 알림 연동 (expo-notifications 를 부르는 유일한 자리, tests/architecture 가 확인)
 * 사용 가능 여부, 권한, Android 채널, Expo 푸시 토큰, 앱이 켜져 있을 때 표시, 알림 누름
 */

/** 알림을 쓸 수 없는 이유 (쓸 수 있으면 undefined) */
export function getPushUnavailableReason(): PushUnavailableReason | undefined {
  if (Platform.OS === 'web') return 'WEB';
  if (!Device.isDevice) return 'NOT_PHYSICAL_DEVICE';
  if (pushConfig.easProjectId === undefined) return 'NO_PROJECT_ID';
  return undefined;
}

export function getPushPlatform(): PushPlatform {
  return Platform.OS === 'ios' ? 'IOS' : 'ANDROID';
}

/** OS 알림 권한이 허용 상태인지 (묻지 않음) */
export async function isPushPermissionGranted(): Promise<boolean> {
  return isGrantedStatus(await Notifications.getPermissionsAsync());
}

/** OS 알림 권한 요청 (이미 거부해 다시 물을 수 없으면 창 없이 false) */
export async function requestPushPermission(): Promise<boolean> {
  return isGrantedStatus(await Notifications.requestPermissionsAsync());
}

// iOS 는 ios.status 기준: 조용한 알림 허용(PROVISIONAL)도 허용으로 봄
function isGrantedStatus(permissions: Notifications.NotificationPermissionsStatus): boolean {
  return permissions.granted || permissions.ios?.status === IosAuthorizationStatus.PROVISIONAL;
}

/** Android 긴급 알림 채널 만들기 (중요도 높음). Android 13 은 채널이 있어야 권한 창이 떠서 권한 요청보다 먼저 부름 */
export async function ensureUrgentAlertChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;
  await Notifications.setNotificationChannelAsync(pushConfig.urgentAlertChannelId, {
    name: pushConfig.urgentAlertChannelName,
    importance: AndroidImportance.HIGH,
  });
}

/** Expo 푸시 토큰 발급 (예: ExponentPushToken[xxxx]). 네트워크를 타므로 실패할 수 있음 */
export async function fetchExpoPushToken(): Promise<string> {
  return (await Notifications.getExpoPushTokenAsync({ projectId: pushConfig.easProjectId })).data;
}

/** 앱이 켜져 있을 때 온 알림도 배너·알림 목록에 표시 */
export function showNotificationsWhileAppIsOpen(): void {
  if (Platform.OS === 'web') return;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

/**
 * 알림 누름 구독: 앱을 연 알림(꺼진 상태에서 누른 경우) 1회 > 이후 누를 때마다
 * 듣는 쪽에는 알림 data 만 넘김. 돌려주는 함수로 구독 해제
 */
export function subscribeToOpenedNotifications(onNotificationOpened: (notificationData: unknown) => void): () => void {
  if (Platform.OS === 'web') return () => {};
  const handleResponse = (response: Notifications.NotificationResponse) => {
    onNotificationOpened(response.notification.request.content.data);
    // 처리한 알림을 지움 (앱을 다시 그릴 때 같은 알림으로 또 이동하지 않게)
    Notifications.clearLastNotificationResponse();
  };
  const launchResponse = Notifications.getLastNotificationResponse();
  if (launchResponse !== null) handleResponse(launchResponse);
  const subscription = Notifications.addNotificationResponseReceivedListener(handleResponse);
  return () => subscription.remove();
}
