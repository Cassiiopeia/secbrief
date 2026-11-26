import { useQuery } from '@tanstack/react-query';

import { KISA_SOURCE_CODES } from '@/data/models/article-source-code';
import { fetchArticlePage } from '@/data/sources/article-source';

import { toAlertLevelView } from './alert-level-view';

/** KISA 경보단계 최신 1건 (오늘 탭 위쪽 띠). 본문이 이 조회를 기다리므로 실패하면 다시 시도하지 않고 바로 띠 없이 보여 줌 */
export function useKisaAlertLevel() {
  return useQuery({
    queryKey: ['articles', 'kisa-alert-level'],
    queryFn: () => fetchArticlePage({ kind: 'advisory', region: 'domestic', sourceCode: KISA_SOURCE_CODES.alertLevel }),
    select: (articlePage) => toAlertLevelView(articlePage.items, new Date()) ?? null,
    retry: false,
  });
}
