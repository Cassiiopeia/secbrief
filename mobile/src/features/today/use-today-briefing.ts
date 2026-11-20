import { useQuery } from '@tanstack/react-query';

import { fetchSampleTodayBriefing } from '@/data/sources/sample-today-briefing-source';

import { toTodayViewState } from './today-view-state';

// React Query 캐시 키: 오늘 브리핑
export const TODAY_BRIEFING_QUERY_KEY = ['today-briefing'] as const;

/** 오늘 브리핑 조회: 예시 소스 > 화면 표시 상태 (서버 API 가 생기면 소스만 교체) */
export function useTodayBriefing() {
  return useQuery({
    queryKey: TODAY_BRIEFING_QUERY_KEY,
    queryFn: fetchSampleTodayBriefing,
    select: toTodayViewState,
  });
}
