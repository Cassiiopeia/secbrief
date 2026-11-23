import { useState } from 'react';
import { View } from 'react-native';

import { SbFilterChips, type SbFilterChipOption, SbMasthead, SbScreen } from '@/design';

import { ArticleInfiniteList } from '../articles/article-infinite-list';
import { usePullRefresh } from '../articles/use-pull-refresh';
import { type NewsRegionFilter, useNewsArticles } from './use-news-articles';

const MASTHEAD_TITLE_LINES = ['뉴스'];
const REGION_FILTER_OPTIONS: SbFilterChipOption<NewsRegionFilter>[] = [
  { value: 'all', label: '전체' },
  { value: 'domestic', label: '국내' },
  { value: 'overseas', label: '해외' },
];

/** 뉴스 탭: 머리 > 지역 칩 > 무한 목록 (항목 누르면 원문) */
export function NewsScreen() {
  const [regionFilter, setRegionFilter] = useState<NewsRegionFilter>('all');
  const newsQuery = useNewsArticles(regionFilter);
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(newsQuery.refetch);

  return (
    <SbScreen>
      <ArticleInfiniteList
        articlePagesQuery={newsQuery}
        listHeader={
          <View>
            <SbMasthead titleLines={MASTHEAD_TITLE_LINES} />
            <SbFilterChips options={REGION_FILTER_OPTIONS} selectedValue={regionFilter} onSelected={setRegionFilter} />
          </View>
        }
        emptyTitle="아직 모은 소식이 없어요"
        emptyDescription="새 소식이 올라오면 여기에 보여 드릴게요."
        isPullRefreshing={isPullRefreshing}
        onPullRefresh={onPullRefresh}
      />
    </SbScreen>
  );
}
