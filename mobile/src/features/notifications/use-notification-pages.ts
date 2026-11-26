import { useInfiniteQuery } from '@tanstack/react-query';

import type { AppNotificationKind } from '@/data/models/app-notification';
import { fetchAppNotificationPage } from '@/data/sources/notification-source';

/** React Query 캐시 키: 알림함 종류별 목록 */
export const toNotificationPagesQueryKey = (kind: AppNotificationKind) => ['notifications', 'pages', kind] as const;

/** 알림함 무한 목록 조회: 종류별 캐시, 다음 페이지는 서버 커서로 */
export function useNotificationPages(kind: AppNotificationKind) {
  return useInfiniteQuery({
    queryKey: toNotificationPagesQueryKey(kind),
    queryFn: ({ pageParam }) => fetchAppNotificationPage(kind, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });
}
