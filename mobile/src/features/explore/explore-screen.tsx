import { router } from 'expo-router';
import { ScrollView, StyleSheet } from 'react-native';

import { SbMasthead, SbScreen, SbSearchEntry, createSbRefreshControl, sbSpacing, useSbTheme } from '@/design';

import { usePullRefresh } from '../articles/use-pull-refresh';
import { useTrendingKeywords } from '../keywords/use-trending-keywords';
import { APP_ROUTES } from '../navigation/app-routes';
import { useWeeklyTrend } from '../trend/use-weekly-trend';
import { ExploreEventSection } from './explore-event-section';
import { ExploreKeywordSection } from './explore-keyword-section';
import { ExploreTrendSection } from './explore-trend-section';
import { useEventArticles } from './use-event-articles';

const MASTHEAD_TITLE_LINES = ['탐색'];

/** 탐색 탭: 머리 > 검색 들머리(검색 입구는 이것 하나) > 이번 주 키워드 순위 > 주제별 주간 동향 > 행사 소식 타임라인. 당겨서 세 구역 모두 새로고침 */
export function ExploreScreen() {
  const { colors } = useSbTheme();
  const trendingKeywordsQuery = useTrendingKeywords();
  const weeklyTrendQuery = useWeeklyTrend();
  const eventArticlesQuery = useEventArticles();
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(() =>
    Promise.all([trendingKeywordsQuery.refetch(), weeklyTrendQuery.refetch(), eventArticlesQuery.refetch()]),
  );

  return (
    <SbScreen>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={createSbRefreshControl({ colors, isRefreshing: isPullRefreshing, onRefresh: onPullRefresh })}>
        <SbMasthead titleLines={MASTHEAD_TITLE_LINES} />
        <SbSearchEntry
          placeholder="제품, 회사, 취약점 번호로 찾아요"
          accessibilityLabel="소식 검색 화면 열기"
          onPress={() => router.push(APP_ROUTES.search)}
        />
        <ExploreKeywordSection />
        <ExploreTrendSection weeklyTrendQuery={weeklyTrendQuery} />
        <ExploreEventSection eventArticlesQuery={eventArticlesQuery} />
      </ScrollView>
    </SbScreen>
  );
}

const styles = StyleSheet.create({
  content: { flexGrow: 1, paddingBottom: sbSpacing.extraLarge },
});
