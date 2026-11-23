import { RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import { toMastheadDate } from '@/core/format/briefing-date-formatter';
import {
  SbEmptyState,
  SbErrorState,
  SbListRow,
  SbLoadingSkeleton,
  SbMasthead,
  SbScreen,
  SbStatTile,
  sbSpacing,
  useSbTheme,
} from '@/design';

import { usePullRefresh } from '../articles/use-pull-refresh';
import { toTrendTopicView } from './trend-view-state';
import { useWeeklyTrend } from './use-weekly-trend';

const MASTHEAD_TITLE_LINES = ['동향'];

/** 동향 탭: 머리(주 시작일) > 1위 주제 타일 > 나머지 주제 목록 (주제 8종, 이번 주 건수 순) */
export function TrendScreen() {
  const { colors } = useSbTheme();
  const weeklyTrendQuery = useWeeklyTrend();
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(weeklyTrendQuery.refetch);

  return (
    <SbScreen>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={isPullRefreshing} onRefresh={onPullRefresh} tintColor={colors.label} />}>
        {renderBody()}
      </ScrollView>
    </SbScreen>
  );

  /** 상태별 본문: 로딩 / 오류 / 빈 상태(이번 주 0건) / 동향 */
  function renderBody() {
    if (weeklyTrendQuery.isPending) {
      return (
        <>
          <SbMasthead titleLines={MASTHEAD_TITLE_LINES} />
          <SbLoadingSkeleton />
        </>
      );
    }
    if (weeklyTrendQuery.isError) {
      const failure = toAppFailure(weeklyTrendQuery.error);
      return (
        <>
          <SbMasthead titleLines={MASTHEAD_TITLE_LINES} />
          <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => weeklyTrendQuery.refetch()} />
        </>
      );
    }
    const weeklyTrend = weeklyTrendQuery.data;
    const mastheadTrailingLines = [`${toMastheadDate(weeklyTrend.weekStartDate)} 주`];
    const trendViews = weeklyTrend.topics.map(toTrendTopicView);
    const hasNoArticleThisWeek = weeklyTrend.topics.every((topic) => topic.weeklyCount === 0);
    if (hasNoArticleThisWeek || trendViews.length === 0) {
      return (
        <>
          <SbMasthead titleLines={MASTHEAD_TITLE_LINES} trailingLines={mastheadTrailingLines} />
          <SbEmptyState title="이번 주 소식을 모으고 있어요" description="소식이 쌓이면 주제별로 정리해 드릴게요." />
        </>
      );
    }
    const [topTrendView, ...otherTrendViews] = trendViews;
    return (
      <>
        <SbMasthead titleLines={MASTHEAD_TITLE_LINES} trailingLines={mastheadTrailingLines} />
        <View style={styles.topTile}>
          <SbStatTile label="이번 주 1위" value={{ text: topTrendView.topicLabel }} detail={topTrendView.detail} tone="feature" />
        </View>
        {otherTrendViews.map((trendView) => (
          <SbListRow
            key={trendView.topic}
            title={trendView.topicLabel}
            detail={trendView.detail}
            accessibilityLabel={`${trendView.topicLabel}, ${trendView.detail}`}
          />
        ))}
      </>
    );
  }
}

const styles = StyleSheet.create({
  // 오류·빈 상태가 남은 높이 가운데에 오도록
  content: { flexGrow: 1 },
  topTile: { flexDirection: 'row', paddingHorizontal: sbSpacing.tileHorizontal, paddingBottom: sbSpacing.medium },
});
