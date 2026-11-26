import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';

import type { ArticleRowView } from './article-row-view';

/** React Query 캐시 키: 목록에서 넘겨받은 기사 표시 내용 (상세 화면이 조회 전에 먼저 그림) */
export const toArticlePreviewQueryKey = (articleId: string) => ['articles', 'preview', articleId] as const;

/**
 * 기사 열기 함수: 목록에서 가진 표시 내용을 캐시에 남기고 앱 안 상세 화면으로 이동
 * (브라우저로 바로 내보내지 않음. 원문은 상세 화면의 원문 보기 버튼에서)
 */
export function useArticleOpener(): (rowView: ArticleRowView) => void {
  const queryClient = useQueryClient();
  return (rowView: ArticleRowView) => {
    queryClient.setQueryData(toArticlePreviewQueryKey(rowView.id), rowView);
    router.push(`/articles/${encodeURIComponent(rowView.id)}`);
  };
}
