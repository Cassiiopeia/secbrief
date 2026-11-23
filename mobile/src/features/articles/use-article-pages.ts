import { useInfiniteQuery } from '@tanstack/react-query';

import type { ArticleFilter } from '@/data/models/briefing-item';
import { fetchArticlePage } from '@/data/sources/article-source';

/** 소식 무한 목록 조회: 필터별 캐시, 다음 페이지는 서버 커서로 */
export function useArticlePages(queryKey: readonly unknown[], filter: ArticleFilter) {
  return useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => fetchArticlePage(filter, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  });
}
