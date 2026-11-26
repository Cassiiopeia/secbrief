import type { ArticleFilter } from '@/data/models/briefing-item';

import { useArticlePages } from './use-article-pages';

/** 중요도 보기 2종: important AI 가 고른 주요 소식만, all 전체 */
export type ImportanceView = 'important' | 'all';

/**
 * 중요도 보기에 따른 소식 목록: 주요 보기는 notable 이상만 조회
 * 주요 소식이 한 건도 없으면(AI 판단 전) 전체 목록으로 대신하고 그 사실을 알려 줌 (화면이 비지 않게)
 */
export function useImportanceFilteredArticles(queryKey: readonly unknown[], baseFilter: ArticleFilter, importanceView: ImportanceView) {
  const isImportantView = importanceView === 'important';
  const importantQuery = useArticlePages([...queryKey, 'important'], { ...baseFilter, minimumImportance: 'notable' }, isImportantView);
  const hasNoImportantArticle =
    isImportantView && importantQuery.isSuccess && importantQuery.data.pages.every((page) => page.items.length === 0);
  const isShowingAllInstead = hasNoImportantArticle;
  const allQuery = useArticlePages([...queryKey, 'all'], baseFilter, !isImportantView || isShowingAllInstead);
  return {
    articlePagesQuery: !isImportantView || isShowingAllInstead ? allQuery : importantQuery,
    isShowingAllInstead,
  };
}
