import { useQuery } from '@tanstack/react-query';

import { fetchArticlePage } from '@/data/sources/article-source';

/** 행사 소식 첫 페이지 (탐색 탭 타임라인). 자동 재시도 없음 (구역이 오류와 다시 시도 버튼을 보여 줌) */
export function useEventArticles() {
  return useQuery({
    queryKey: ['articles', 'event-timeline'],
    queryFn: () => fetchArticlePage({ topic: 'event' }),
    // 행사 주제를 모르는 이전 서버가 다른 소식을 돌려줘도 섞이지 않게 한 번 더 거름
    select: (articlePage) => articlePage.items.filter((item) => item.topic === 'event'),
    retry: false,
  });
}
