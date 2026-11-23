import { useQuery } from '@tanstack/react-query';

import { fetchCollectSourceStatuses } from '@/data/sources/collect-source-status-source';

// React Query 캐시 키: 수집 출처 상태
export const COLLECT_SOURCE_STATUSES_QUERY_KEY = ['collect-source-statuses'] as const;

/** 수집 출처 상태 조회 */
export function useCollectSourceStatuses() {
  return useQuery({ queryKey: COLLECT_SOURCE_STATUSES_QUERY_KEY, queryFn: fetchCollectSourceStatuses });
}
