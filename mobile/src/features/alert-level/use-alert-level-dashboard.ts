import { useQuery } from '@tanstack/react-query';

import type { AlertLevelDashboard } from '@/data/models/alert-level-dashboard';
import { fetchAlertLevelDashboard } from '@/data/sources/alert-level-source';

/** React Query 캐시 키: 경보단계 화면과 홈 경보 띠가 같은 조회를 나눠 씀 */
export const ALERT_LEVEL_DASHBOARD_QUERY_KEY = ['alert-level'] as const;

/** 경보단계 화면 조회 (select 로 화면마다 필요한 모양만 꺼냄) */
export function useAlertLevelDashboard<Selected = AlertLevelDashboard>(
  select?: (dashboard: AlertLevelDashboard) => Selected,
  isRetryEnabled: boolean = true,
) {
  return useQuery({
    queryKey: ALERT_LEVEL_DASHBOARD_QUERY_KEY,
    queryFn: fetchAlertLevelDashboard,
    select,
    retry: isRetryEnabled ? undefined : false,
  });
}
