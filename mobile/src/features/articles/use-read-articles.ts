import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';

import { readReadArticleIds, saveReadArticleIds } from '@/data/sources/read-article-storage';

import { toReadArticleIdsWith } from './read-article-ids';

// React Query 캐시 키: 기기에 저장된 읽은 기사 ID
const READ_ARTICLE_IDS_QUERY_KEY = ['read-article-ids'] as const;
const NO_READ_ARTICLE_IDS: string[] = [];

/** 읽은 기사 ID 묶음 (기기 저장값, 앱이 켜져 있는 동안 캐시). 목록에서 읽은 소식을 흐리게 보여 줄 때 씀 */
export function useReadArticleIds(): Set<string> {
  const readArticleIdsQuery = useQuery({ queryKey: READ_ARTICLE_IDS_QUERY_KEY, queryFn: readReadArticleIds, staleTime: Infinity });
  return new Set(readArticleIdsQuery.data ?? NO_READ_ARTICLE_IDS);
}

/** 기사를 읽음으로 표시하는 함수: 캐시를 먼저 바꾸고 기기에 저장 (저장 실패는 조용히 넘김: 다음에 다시 읽으면 됨) */
export function useMarkArticleRead(): (articleId: string) => void {
  const queryClient = useQueryClient();
  return useCallback(
    (articleId: string) => {
      // 기기 저장값을 먼저 읽어 온 뒤 더함 (앱을 켜자마자 상세로 들어와도 이전 기록을 덮어쓰지 않게)
      void queryClient
        .ensureQueryData({ queryKey: READ_ARTICLE_IDS_QUERY_KEY, queryFn: readReadArticleIds })
        .then((readArticleIds) => {
          if (readArticleIds[readArticleIds.length - 1] === articleId) return;
          const nextReadArticleIds = toReadArticleIdsWith(readArticleIds, articleId);
          queryClient.setQueryData(READ_ARTICLE_IDS_QUERY_KEY, nextReadArticleIds);
          return saveReadArticleIds(nextReadArticleIds);
        })
        .catch(() => undefined);
    },
    [queryClient],
  );
}
