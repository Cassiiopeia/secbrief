import { useQuery, useQueryClient } from '@tanstack/react-query';

import { fetchArticleDetail } from '@/data/sources/article-detail-source';

import type { ArticleRowView } from '../articles/article-row-view';
import { toArticlePreviewQueryKey } from '../articles/use-article-opener';
import { toArticleDetailView } from './article-detail-view';

/**
 * 기사 상세 조회 + 목록에서 넘겨받은 표시 내용
 * 조회가 끝나기 전이나 실패했을 때도 넘겨받은 내용으로 제목·원문 버튼을 먼저 그림
 */
export function useArticleDetail(articleId: string) {
  const queryClient = useQueryClient();
  const previewArticle = queryClient.getQueryData<ArticleRowView>(toArticlePreviewQueryKey(articleId));
  const detailQuery = useQuery({
    queryKey: ['articles', 'detail', articleId],
    queryFn: () => fetchArticleDetail(articleId),
    select: (detail) => toArticleDetailView(detail, new Date()),
  });
  return { detailQuery, article: detailQuery.data?.article ?? previewArticle };
}
