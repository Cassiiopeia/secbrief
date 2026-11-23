import { useArticlePages } from '../articles/use-article-pages';

/** 뉴스 지역 필터: all 전체, domestic 국내, overseas 해외 */
export type NewsRegionFilter = 'all' | 'domestic' | 'overseas';

/** 뉴스 무한 목록 (지역 필터별 캐시) */
export function useNewsArticles(regionFilter: NewsRegionFilter) {
  return useArticlePages(['articles', 'news', regionFilter], {
    kind: 'news',
    region: regionFilter === 'all' ? undefined : regionFilter,
  });
}
