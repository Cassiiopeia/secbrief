import { useQuery } from '@tanstack/react-query';

import { fetchWeeklyTrend } from '@/data/sources/weekly-trend-source';

// React Query 캐시 키: 이번 주 동향
export const WEEKLY_TREND_QUERY_KEY = ['weekly-trend'] as const;

/** 이번 주 동향 조회 */
export function useWeeklyTrend() {
  return useQuery({ queryKey: WEEKLY_TREND_QUERY_KEY, queryFn: fetchWeeklyTrend });
}
