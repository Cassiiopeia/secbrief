import { useQuery } from '@tanstack/react-query';

import { fetchTodayBriefing } from '@/data/sources/today-briefing-source';

import { toTodayViewState } from './today-view-state';

// React Query 캐시 키: 오늘 브리핑
export const TODAY_BRIEFING_QUERY_KEY = ['today-briefing'] as const;

/** 오늘 브리핑 조회: 서버 > 화면 표시 상태 (상대 시각은 변환 시점 기준) */
export function useTodayBriefing() {
  return useQuery({
    queryKey: TODAY_BRIEFING_QUERY_KEY,
    queryFn: fetchTodayBriefing,
    select: (briefing) => toTodayViewState(briefing, new Date()),
  });
}
