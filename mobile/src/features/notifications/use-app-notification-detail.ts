import { useQuery } from '@tanstack/react-query';

import { toAppFailure } from '@/core/error/app-failure';
import { fetchAppNotificationDetail } from '@/data/sources/notification-source';

// 서버 ErrorCode: 없거나 보관 기간이 지난 글 (다시 불러도 같아 재시도하지 않음)
export const NOTIFICATION_NOT_FOUND_ERROR_CODE = 'RESOURCE_NOT_FOUND';
// 그 밖의 실패를 다시 부르는 횟수 (React Query 기본값과 같음)
const MAXIMUM_RETRY_COUNT = 3;

/** 알림함 상세 조회 (번호별 캐시, 찾지 못함은 곧바로 실패) */
export function useAppNotificationDetail(notificationId: string) {
  return useQuery({
    queryKey: ['notifications', 'detail', notificationId],
    queryFn: () => fetchAppNotificationDetail(notificationId),
    enabled: notificationId.length > 0,
    retry: (failureCount, error) =>
      toAppFailure(error).errorCode !== NOTIFICATION_NOT_FOUND_ERROR_CODE && failureCount < MAXIMUM_RETRY_COUNT,
  });
}
