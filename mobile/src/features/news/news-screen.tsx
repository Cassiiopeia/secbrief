import { router, useLocalSearchParams } from 'expo-router';
import { View } from 'react-native';

import { SbFilterChips, type SbFilterChipOption, SbInlineNotice, SbMasthead, SbScreen } from '@/design';

import { ArticleInfiniteList } from '../articles/article-infinite-list';
import { toTopicLabel } from '../articles/article-labels';
import { useImportanceFilteredArticles } from '../articles/use-importance-filtered-articles';
import { usePullRefresh } from '../articles/use-pull-refresh';
import { SEARCH_MASTHEAD_ACTION } from '../search/search-action';
import {
  NEWS_CHIP_TOPICS,
  type NewsCategory,
  type NewsRouteParams,
  type NewsTopicFilter,
  hasTopicChips,
  toNewsFeedQuery,
  toNewsRouteParams,
  toNewsSelection,
} from './news-feed-filter';

const MASTHEAD_TITLE_LINES = ['소식'];
const CATEGORY_OPTIONS: SbFilterChipOption<NewsCategory>[] = [
  { value: 'important', label: '주요만' },
  { value: 'all', label: '전체' },
  { value: 'domestic', label: '국내' },
  { value: 'overseas', label: '해외' },
  { value: 'event', label: '행사' },
];
const TOPIC_OPTIONS: SbFilterChipOption<NewsTopicFilter>[] = [
  { value: 'all', label: '모든 주제' },
  ...NEWS_CHIP_TOPICS.map((topic) => ({ value: topic, label: toTopicLabel(topic) })),
];

/**
 * 소식 탭: 머리 > 카테고리 칩 > 주제 칩 > 맨 위 대표 기사(큰 카드) + 중요도별 카드·행 무한 목록. 기본은 AI 가 고른 주요만
 * 고른 칩은 경로 파라미터에 둠 (홈의 전체 보기가 카테고리를 정해서 열 수 있게)
 */
export function NewsScreen() {
  const selection = toNewsSelection(useLocalSearchParams<NewsRouteParams>());
  const { category, topicFilter } = selection;
  const { baseFilter, importanceView } = toNewsFeedQuery(category, topicFilter);
  const { articlePagesQuery, isShowingAllInstead } = useImportanceFilteredArticles(
    ['articles', 'feed', baseFilter.kind ?? 'any', baseFilter.region ?? 'any', baseFilter.topic ?? 'any'],
    baseFilter,
    importanceView,
  );
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(articlePagesQuery.refetch);
  const onCategorySelected = (selectedCategory: NewsCategory) =>
    router.setParams(toNewsRouteParams({ ...selection, category: selectedCategory }));
  const onTopicSelected = (selectedTopicFilter: NewsTopicFilter) =>
    router.setParams(toNewsRouteParams({ ...selection, topicFilter: selectedTopicFilter }));

  return (
    <SbScreen>
      <ArticleInfiniteList
        articlePagesQuery={articlePagesQuery}
        listPurpose="feed"
        hasLeadCard
        impliedKind={baseFilter.kind}
        skeletonVariant="articleRows"
        listHeader={
          <View>
            <SbMasthead titleLines={MASTHEAD_TITLE_LINES} action={SEARCH_MASTHEAD_ACTION} />
            <SbFilterChips options={CATEGORY_OPTIONS} selectedValue={category} onSelected={onCategorySelected} />
            {hasTopicChips(category) && <SbFilterChips options={TOPIC_OPTIONS} selectedValue={topicFilter} onSelected={onTopicSelected} tone="quiet" />}
            {isShowingAllInstead && <SbInlineNotice message="AI 가 고른 주요 소식이 아직 없어서 전체 소식을 보여 드려요." />}
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
