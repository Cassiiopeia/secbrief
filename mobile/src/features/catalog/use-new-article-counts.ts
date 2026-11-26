import { useQuery } from '@tanstack/react-query';

import { getDeviceTimeZone } from '@/data/sources/device-time-zone';
import { fetchNewArticleCounts } from '@/data/sources/new-article-counts-source';

// React Query 캐시 키: 오늘 새 소식 건수 (기기 시간대별)
export const NEW_ARTICLE_COUNTS_QUERY_KEY = ['new-article-counts'] as const;
// 건수를 다시 묻지 않는 시간 (1분, 수집 틱보다 짧게)
const NEW_ARTICLE_COUNTS_STALE_MILLISECONDS = 60 * 1000;

/** 오늘(기기 날짜) 새 소식 건수: 홈 띠와 취약점 탭 칩 +N 이 함께 씀. 실패하면 data 없음 (그 자리만 숨김) */
export function useNewArticleCounts() {
  const timeZone = getDeviceTimeZone();
  return useQuery({
    queryKey: [...NEW_ARTICLE_COUNTS_QUERY_KEY, timeZone],
    queryFn: () => fetchNewArticleCounts(timeZone),
    staleTime: NEW_ARTICLE_COUNTS_STALE_MILLISECONDS,
  });
}
