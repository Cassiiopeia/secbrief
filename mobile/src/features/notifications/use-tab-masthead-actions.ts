import { router } from 'expo-router';

import type { SbMastheadAction } from '@/design';

import { APP_ROUTES } from '../navigation/app-routes';
import { SEARCH_MASTHEAD_ACTION } from '../search/search-action';
import { useUnseenNotificationDots } from './use-unseen-notification-dots';

/** 탭 머리 오른쪽 버튼 2개 (홈·소식·취약점·더보기 공통): 검색, 알림함(안 본 글이 있으면 점) */
export function useTabMastheadActions(): SbMastheadAction[] {
  const { hasUnseenAny } = useUnseenNotificationDots();
  return [
    SEARCH_MASTHEAD_ACTION,
    {
      iconName: 'notifications-none',
      accessibilityLabel: hasUnseenAny ? '알림함, 새 알림 있음' : '알림함',
      onPress: () => router.push(APP_ROUTES.notifications),
      hasDot: hasUnseenAny,
    },
  ];
}
