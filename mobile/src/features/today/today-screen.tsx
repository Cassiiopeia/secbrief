import { ScrollView, StyleSheet } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import { SbEmptyState, SbErrorState, SbLoadingSkeleton, SbMasthead, SbScreen, createSbRefreshControl, useSbTheme } from '@/design';

import { usePullRefresh } from '../articles/use-pull-refresh';
import { TodayBriefingContent } from './today-briefing-content';
import { useTabMastheadActions } from '../notifications/use-tab-masthead-actions';
import { useKisaAlertLevel } from './use-kisa-alert-level';
import { useTodayBriefing } from './use-today-briefing';

const MASTHEAD_TITLE_LINES = ['SEC', 'BRIEF'];

/** 오늘 탭: 경보단계 띠 > 주요 소식 > 긴급 > 숫자 > 주제 바로가기 > 최신 스트림. 당겨서 새로고침 */
export function TodayScreen() {
  const mastheadActions = useTabMastheadActions();
  const { colors } = useSbTheme();
  const briefingQuery = useTodayBriefing();
  const alertLevelQuery = useKisaAlertLevel();
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(() => Promise.all([briefingQuery.refetch(), alertLevelQuery.refetch()]));

  return (
    <SbScreen>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={createSbRefreshControl({ colors, isRefreshing: isPullRefreshing, onRefresh: onPullRefresh })}>
        {renderBody()}
      </ScrollView>
    </SbScreen>
  );

  /** 상태별 본문: 오류 / 로딩 뼈대 / 빈 상태 / 브리핑 */
  function renderBody() {
    if (briefingQuery.isError) {
      const failure = toAppFailure(briefingQuery.error);
      return (
        <>
          <SbMasthead titleLines={MASTHEAD_TITLE_LINES} actions={mastheadActions} />
          <SbErrorState
            message={failure.userMessage}
            errorCode={failure.errorCode}
            onRetry={() => briefingQuery.refetch()}
          />
        </>
      );
    }
    // 경보단계 조회까지 끝난 뒤 본문을 그림 (띠가 늦게 끼어들어 화면이 밀리지 않게. 실패하면 띠 없이)
    if (briefingQuery.isPending || alertLevelQuery.isPending) {
      return (
        <>
          <SbMasthead titleLines={MASTHEAD_TITLE_LINES} actions={mastheadActions} />
          <SbLoadingSkeleton />
        </>
      );
    }
    if (briefingQuery.data.isEmpty) {
      return (
        <>
          <SbMasthead
            titleLines={MASTHEAD_TITLE_LINES}
            trailingLines={briefingQuery.data.mastheadTrailingLines}
            actions={mastheadActions}
          />
          <SbEmptyState illustration="feedEmpty" title="오늘은 새 소식이 없어요" description="새 소식이 올라오면 여기에 보여 드릴게요." />
        </>
      );
    }
    return (
      <TodayBriefingContent
        mastheadTitleLines={MASTHEAD_TITLE_LINES}
        state={briefingQuery.data}
        alertLevel={alertLevelQuery.data ?? undefined}
      />
    );
  }
}

const styles = StyleSheet.create({
  // 오류·빈 상태가 남은 높이 가운데에 오도록
  content: { flexGrow: 1 },
});
