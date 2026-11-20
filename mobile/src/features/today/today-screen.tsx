import { useState } from 'react';
import { RefreshControl, ScrollView, StyleSheet } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import { SbEmptyState, SbErrorState, SbLoadingSkeleton, SbMasthead, SbScreen, useSbTheme } from '@/design';

import { TodayBriefingContent } from './today-briefing-content';
import { useTodayBriefing } from './use-today-briefing';

const MASTHEAD_TITLE_LINES = ['SEC', 'BRIEF'];

/** 오늘 탭: 긴급 소식 > 통계 타일 > 시간순 스트림. 당겨서 새로고침 */
export function TodayScreen() {
  const { colors } = useSbTheme();
  const briefingQuery = useTodayBriefing();
  // 사용자가 당겨서 시작한 새로고침만 표시 (자동 재조회 때는 돌지 않게)
  const [isPullRefreshing, setIsPullRefreshing] = useState(false);

  const onPullRefresh = async () => {
    setIsPullRefreshing(true);
    try {
      await briefingQuery.refetch();
    } finally {
      setIsPullRefreshing(false); // (성공·실패 공통)
    }
  };

  return (
    <SbScreen>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl refreshing={isPullRefreshing} onRefresh={onPullRefresh} tintColor={colors.label} />
        }>
        {renderBody()}
      </ScrollView>
    </SbScreen>
  );

  /** 상태별 본문: 로딩 뼈대 / 오류 / 빈 상태 / 브리핑 */
  function renderBody() {
    if (briefingQuery.isPending) {
      return (
        <>
          <SbMasthead titleLines={MASTHEAD_TITLE_LINES} />
          <SbLoadingSkeleton />
        </>
      );
    }
    if (briefingQuery.isError) {
      const failure = toAppFailure(briefingQuery.error);
      return (
        <>
          <SbMasthead titleLines={MASTHEAD_TITLE_LINES} />
          <SbErrorState
            message={failure.userMessage}
            errorCode={failure.errorCode}
            onRetry={() => briefingQuery.refetch()}
          />
        </>
      );
    }
    if (briefingQuery.data.isEmpty) {
      return (
        <>
          <SbMasthead titleLines={MASTHEAD_TITLE_LINES} trailingLines={briefingQuery.data.mastheadTrailingLines} />
          <SbEmptyState title="오늘은 새 소식이 없어요" description="새 소식이 올라오면 여기에 보여 드릴게요." />
        </>
      );
    }
    return <TodayBriefingContent mastheadTitleLines={MASTHEAD_TITLE_LINES} state={briefingQuery.data} />;
  }
}

const styles = StyleSheet.create({
  // 오류·빈 상태가 남은 높이 가운데에 오도록
  content: { flexGrow: 1 },
});
