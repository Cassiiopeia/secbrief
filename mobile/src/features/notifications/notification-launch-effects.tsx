import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useCallback, useEffect, useRef } from 'react';

import { showNotificationsWhileAppIsOpen, subscribeToOpenedNotifications } from '@/data/sources/device-notification-client';

import { useIsRootNavigatorReady } from '../navigation/use-is-root-navigator-ready';
import { type NotificationTarget, isTabRoute, toNotificationTarget } from './notification-target-route';
import { syncPushRegistrationOnLaunch } from './push-registration-service';
import { ENABLED_ALERT_CATEGORIES_QUERY_KEY } from './use-alert-category-settings';
import { useNotificationReadStateUpdaters } from './use-notification-read-state';
import { QUIET_HOURS_QUERY_KEY } from './use-quiet-hours-settings';
import { LATEST_NOTIFICATION_IDS_QUERY_KEY } from './use-unseen-notification-dots';

/**
 * 앱 시작 알림 처리 3개 (화면 없음): 켜져 있을 때 배너 표시, 누른 알림이 가리키는 화면으로 이동, 서버 등록 동기화
 * 이동은 화면 스택이 준비된 뒤 (꺼진 앱을 알림으로 연 경우 첫 화면보다 먼저 이동하지 않게)
 */
export function NotificationLaunchEffects() {
  const queryClient = useQueryClient();
  const isNavigatorReady = useIsRootNavigatorReady();
  const { markNotificationOpened } = useNotificationReadStateUpdaters();
  // 누른 알림 중 아직 열지 못한 것 (화면 스택이 준비되면 엶, 여러 번 누르면 마지막 것)
  const pendingTargetRef = useRef<NotificationTarget>(undefined);

  const openPendingTarget = useCallback(() => {
    const pendingTarget = pendingTargetRef.current;
    if (pendingTarget === undefined || !isNavigatorReady) return;
    pendingTargetRef.current = undefined;
    if (pendingTarget.notificationId !== undefined) markNotificationOpened(pendingTarget.notificationId);
    // 새 알림이 왔으니 종 점을 다시 계산
    void queryClient.invalidateQueries({ queryKey: LATEST_NOTIFICATION_IDS_QUERY_KEY });
    // 탭은 바꾸고, 상세·알림함은 위에 쌓음 (뒤로 가면 원래 화면)
    if (isTabRoute(pendingTarget.route)) router.navigate(pendingTarget.route);
    else router.push(pendingTarget.route);
  }, [isNavigatorReady, markNotificationOpened, queryClient]);

  useEffect(() => {
    showNotificationsWhileAppIsOpen();
    // 앱을 연 알림 1회 + 이후 누를 때마다
    return subscribeToOpenedNotifications((notificationData) => {
      pendingTargetRef.current = toNotificationTarget(notificationData);
      openPendingTarget();
    });
  }, [openPendingTarget]);

  // 스택이 준비되는 순간 밀린 알림을 엶
  useEffect(openPendingTarget, [openPendingTarget]);

  useEffect(() => {
    // 실패는 조용히 넘김 (다음 실행에 다시)
    syncPushRegistrationOnLaunch()
      .then((syncedAlertCategories) => {
        queryClient.setQueryData(ENABLED_ALERT_CATEGORIES_QUERY_KEY, syncedAlertCategories);
        // 동기화가 서버 값으로 방해 금지 시간도 맞춤
        void queryClient.invalidateQueries({ queryKey: QUIET_HOURS_QUERY_KEY });
      })
      .catch(() => undefined);
  }, [queryClient]);

  return null;
}
