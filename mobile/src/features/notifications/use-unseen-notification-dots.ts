import { useQuery } from '@tanstack/react-query';

import { fetchLatestAppNotificationIds } from '@/data/sources/notification-source';

import { hasUnseenNotification } from './notification-read-state';
import { useNotificationReadState } from './use-notification-read-state';

// React Query 캐시 키: 종류별 최근 글 번호 (알림함 새로고침 때 함께 다시 부름)
export const LATEST_NOTIFICATION_IDS_QUERY_KEY = ['notifications', 'latest'] as const;
// 최근 글 번호를 다시 부르는 간격 (5분: 서버 긴급 알림 틱과 같음)
const LATEST_NOTIFICATION_IDS_REFETCH_MILLISECONDS = 5 * 60 * 1000;

/** 안 본 글 점 3개: 긴급 알림, 공지사항, 둘 중 하나라도 (종 아이콘). 조회 실패·읽기 전에는 점 없음 */
export function useUnseenNotificationDots() {
  const { readState, isLoaded } = useNotificationReadState();
  const latestIdsQuery = useQuery({
    queryKey: LATEST_NOTIFICATION_IDS_QUERY_KEY,
    queryFn: fetchLatestAppNotificationIds,
    refetchInterval: LATEST_NOTIFICATION_IDS_REFETCH_MILLISECONDS,
  });
  const latestIds = isLoaded ? latestIdsQuery.data : undefined;
  const hasUnseenAlert = hasUnseenNotification(latestIds?.latestAlertId, readState.seenUpToIdByKind.ALERT);
  const hasUnseenNotice = hasUnseenNotification(latestIds?.latestNoticeId, readState.seenUpToIdByKind.NOTICE);
  return { hasUnseenAlert, hasUnseenNotice, hasUnseenAny: hasUnseenAlert || hasUnseenNotice };
}
