import { useQuery } from '@tanstack/react-query';

import { fetchTrendingKeywords } from '@/data/sources/keyword-source';

// 많이 나온 키워드 조회 범위: 최근 7일, 20개
const TRENDING_KEYWORD_DAYS = 7;
const TRENDING_KEYWORD_SIZE = 20;

/** 최근 7일 많이 나온 키워드. 자동 재시도 없음 (홈·검색은 구역을 비우고, 탐색 탭은 오류와 다시 시도 버튼을 보여 줌) */
export function useTrendingKeywords() {
  return useQuery({
    queryKey: ['keywords', 'trending', TRENDING_KEYWORD_DAYS, TRENDING_KEYWORD_SIZE],
    queryFn: () => fetchTrendingKeywords(TRENDING_KEYWORD_DAYS, TRENDING_KEYWORD_SIZE),
    retry: false,
  });
}
