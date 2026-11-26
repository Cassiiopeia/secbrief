import { useQuery } from '@tanstack/react-query';

import { RESOURCE_NOT_FOUND_ERROR_CODE, shouldRetryQuery } from '@/core/error/app-failure';
import { fetchAppNotificationDetail } from '@/data/sources/notification-source';

// 서버 ErrorCode: 없거나 보관 기간이 지난 글
export const NOTIFICATION_NOT_FOUND_ERROR_CODE = RESOURCE_NOT_FOUND_ERROR_CODE;

/** 알림함 상세 조회 (번호별 캐시, 찾지 못함은 곧바로 실패) */
export function useAppNotificationDetail(notificationId: string) {
  return useQuery({
    queryKey: ['notifications', 'detail', notificationId],
    queryFn: () => fetchAppNotificationDetail(notificationId),
    enabled: notificationId.length > 0,
    retry: shouldRetryQuery,
  });
}
