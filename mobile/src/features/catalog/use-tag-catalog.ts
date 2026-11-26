import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo } from 'react';
import { AppState } from 'react-native';

import { EMPTY_TAG_CATALOG, type TagCatalog } from '@/data/models/catalog-tag';
import { readStoredTagCatalog, refreshTagCatalog } from '@/data/sources/catalog-tag-source';

import { type TagCatalogIndex, toTagCatalogIndex } from './tag-catalog-view';

// React Query 캐시 키: 서버 분류 태그 카탈로그
export const TAG_CATALOG_QUERY_KEY = ['tag-catalog'] as const;

// 서버에서 받기, 실패하면 기기 저장본 (화면은 받은 만큼만 그림)
async function loadTagCatalog(): Promise<TagCatalog> {
  try {
    return await refreshTagCatalog();
  } catch {
    return readStoredTagCatalog();
  }
}

/** 카탈로그 색인 (분야·생태계·제품 묶음·제품). 아직 못 받았으면 빈 색인 (태그·칩 없이 그림) */
export function useTagCatalogIndex(): TagCatalogIndex {
  const catalogQuery = useQuery({ queryKey: TAG_CATALOG_QUERY_KEY, queryFn: loadTagCatalog, staleTime: Infinity });
  const catalog = catalogQuery.data ?? EMPTY_TAG_CATALOG;
  return useMemo(() => toTagCatalogIndex(catalog), [catalog]);
}

/**
 * 카탈로그 동기화 (화면 없음, 루트에 1개): 시작하면 저장본을 먼저 넣고 서버에 다시 물음 > 앱이 앞으로 올 때마다 다시 물음
 * 서버가 304 면 저장본 그대로 (항목 추가·이름 변경은 배포 없이 다음 물음에 반영)
 */
export function TagCatalogSyncEffect() {
  const queryClient = useQueryClient();
  useEffect(() => {
    void readStoredTagCatalog().then((storedCatalog) => {
      if (queryClient.getQueryData(TAG_CATALOG_QUERY_KEY) === undefined && storedCatalog.tags.length > 0) {
        queryClient.setQueryData(TAG_CATALOG_QUERY_KEY, storedCatalog);
      }
      void queryClient.invalidateQueries({ queryKey: TAG_CATALOG_QUERY_KEY });
    });
    const subscription = AppState.addEventListener('change', (appStateStatus) => {
      if (appStateStatus === 'active') void queryClient.invalidateQueries({ queryKey: TAG_CATALOG_QUERY_KEY });
    });
    return () => subscription.remove();
  }, [queryClient]);
  return null;
}
