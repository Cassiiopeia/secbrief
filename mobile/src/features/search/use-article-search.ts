import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { fetchArticleSearch } from '@/data/sources/article-search-source';

// 검색 결과 건수 (상위 N건, 다음 페이지 없음)
const SEARCH_RESULT_SIZE = 30;

/** 소식 검색: 검색어가 있을 때만 조회. 다음 검색어 결과가 올 때까지 앞 결과를 그대로 보여 줌 (깜박임 방지) */
export function useArticleSearch(searchQuery: string | undefined) {
  return useQuery({
    queryKey: ['articles', 'search', searchQuery],
    queryFn: () => fetchArticleSearch(searchQuery ?? '', SEARCH_RESULT_SIZE),
    enabled: searchQuery !== undefined,
    placeholderData: keepPreviousData,
  });
}
