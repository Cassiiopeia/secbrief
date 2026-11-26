import { useInfiniteQuery } from '@tanstack/react-query';

import type { ArticleFilter } from '@/data/models/briefing-item';
import { fetchArticlePage } from '@/data/sources/article-source';

import { isArticleMatchingFilter } from './article-filter-guard';

/** 소식 무한 목록 조회: 필터별 캐시, 다음 페이지는 서버 커서로. 받은 항목은 필터 조건으로 한 번 더 거름 */
export function useArticlePages(queryKey: readonly unknown[], filter: ArticleFilter, isEnabled: boolean = true) {
  return useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => fetchArticlePage(filter, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    enabled: isEnabled,
    select: (data) => ({
      ...data,
      pages: data.pages.map((page) => ({ ...page, items: page.items.filter((item) => isArticleMatchingFilter(item, filter)) })),
    }),
  });
}
