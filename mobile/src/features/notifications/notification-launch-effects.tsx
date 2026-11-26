import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useEffect } from 'react';

import { showNotificationsWhileAppIsOpen, subscribeToOpenedNotifications } from '@/data/sources/device-notification-client';

import { toNotificationTargetRoute } from './alert-category-view-state';
import { syncPushRegistrationOnLaunch } from './push-registration-service';
import { ENABLED_ALERT_CATEGORIES_QUERY_KEY } from './use-alert-category-settings';

/**
 * 앱 시작 알림 처리 3개 (화면 없음): 켜져 있을 때 배너 표시, 누른 알림의 탭으로 이동, 서버 등록 동기화
 * 화면 스택 다음에 둠 (스택이 준비된 뒤 이동)
 */
export function NotificationLaunchEffects() {
  const queryClient = useQueryClient();

  useEffect(() => {
    showNotificationsWhileAppIsOpen();
    return subscribeToOpenedNotifications((notificationData) => router.navigate(toNotificationTargetRoute(notificationData)));
  }, []);

  useEffect(() => {
    // 실패는 조용히 넘김 (다음 실행에 다시)
    syncPushRegistrationOnLaunch()
      .then((syncedAlertCategories) => queryClient.setQueryData(ENABLED_ALERT_CATEGORIES_QUERY_KEY, syncedAlertCategories))
      .catch(() => undefined);
  }, [queryClient]);

  return null;
}
