import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { SectionList, StyleSheet, View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import type { AppNotificationKind, AppNotificationSummary } from '@/data/models/app-notification';
import {
  SbEmptyState,
  SbErrorState,
  SbListEntrance,
  SbLoadingSkeleton,
  SbSectionHeader,
  SbText,
  createSbRefreshControl,
  sbSpacing,
  useSbTheme,
} from '@/design';

import { usePullRefresh } from '../articles/use-pull-refresh';
import { toNoticeDetailRoute, toNotificationDetailRoute } from '../navigation/app-routes';
import { NotificationInboxRow } from './notification-inbox-row';
import { toNotificationInboxRowView, toNotificationInboxSections } from './notification-inbox-view';
import type { AppNotificationNewnessBaseline } from './notification-read-state';
import { useNotificationPages } from './use-notification-pages';
import { useNotificationReadState, useNotificationReadStateUpdaters } from './use-notification-read-state';
import { LATEST_NOTIFICATION_IDS_QUERY_KEY } from './use-unseen-notification-dots';

// 종류별 빈 상태 문구·아이콘, 목록 끝 안내 (긴급 알림 기록은 서버가 30일 보관)
const INBOX_TEXTS: Record<AppNotificationKind, { emptyTitle: string; emptyDescription: string; emptyIconName: 'notifications-none' | 'campaign'; endNote?: string }> = {
  ALERT: {
    emptyTitle: '아직 받은 알림이 없어요',
    emptyDescription: '긴급 보안 소식이 들어오면 여기에 모아 둘게요.',
    emptyIconName: 'notifications-none',
    endNote: '최근 30일 동안 보낸 알림을 모아 둬요.',
  },
  NOTICE: {
    emptyTitle: '아직 올라온 공지사항이 없어요',
    emptyDescription: '서비스 소식이 생기면 여기에서 알려 드릴게요.',
    emptyIconName: 'campaign',
  },
};
// 다음 페이지를 불러오는 동안 목록 끝에 보이는 뼈대 행 수
const NEXT_PAGE_SKELETON_ROW_COUNT = 2;

/**
 * 알림함 한 종류의 목록: 날짜 묶음(긴급 알림) 또는 고정 먼저(공지사항) > 끝에 닿으면 다음 페이지 > 상태(로딩·오류·빈 상태)
 * 새 글 기준은 화면을 연 순간의 본 번호로 고정 (목록을 보는 동안 강조가 사라지지 않게), 받은 글은 곧바로 본 것으로 기록
 */
export function NotificationInboxList({ kind }: { kind: AppNotificationKind }) {
  const { colors } = useSbTheme();
  const queryClient = useQueryClient();
  const notificationPagesQuery = useNotificationPages(kind);
  const { readState, isLoaded: isReadStateLoaded } = useNotificationReadState();
  const { markNotificationsSeen } = useNotificationReadStateUpdaters();
  // 화면을 연 순간의 본 번호 (저장값을 다 읽은 뒤 한 번만 잡음, 처음 방문이면 seenUpToId 없음)
  const [openedBaseline, setOpenedBaseline] = useState<{ seenUpToId?: number }>();
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(() =>
    Promise.all([notificationPagesQuery.refetch(), queryClient.invalidateQueries({ queryKey: LATEST_NOTIFICATION_IDS_QUERY_KEY })]),
  );

  const summaries: AppNotificationSummary[] = (notificationPagesQuery.data?.pages ?? []).flatMap((page) => page.items);
  const loadedIdsKey = summaries.map((summary) => summary.id).join(',');

  // 저장값을 다 읽은 첫 그리기에서 한 번만 잡음 (그리는 중 상태 맞추기: 효과를 거치면 한 번 더 그림)
  if (isReadStateLoaded && openedBaseline === undefined) setOpenedBaseline({ seenUpToId: readState.seenUpToIdByKind[kind] });

  useEffect(() => {
    // 기준을 잡기 전에 본 번호를 올리면 이번에 받은 글이 새 글로 보이지 않음
    if (openedBaseline === undefined) return;
    if (loadedIdsKey.length > 0) markNotificationsSeen(kind, loadedIdsKey.split(',').map(Number));
  }, [kind, loadedIdsKey, markNotificationsSeen, openedBaseline]);

  const now = new Date();
  const baseline: AppNotificationNewnessBaseline = {
    seenUpToId: openedBaseline?.seenUpToId,
    openedNotificationIds: new Set(readState.openedNotificationIds),
  };
  const rowViews = openedBaseline !== undefined ? summaries.map((summary) => toNotificationInboxRowView(summary, baseline, now)) : [];
  const sections = toNotificationInboxSections(kind, summaries, rowViews, now);
  const texts = INBOX_TEXTS[kind];
  const isAtEnd = !notificationPagesQuery.hasNextPage && rowViews.length > 0;

  const onEndReached = () => {
    // 이미 불러오는 중이면 다시 부르지 않음
    if (notificationPagesQuery.hasNextPage && !notificationPagesQuery.isFetchingNextPage) void notificationPagesQuery.fetchNextPage();
  };

  return (
    <SectionList
      sections={sections}
      keyExtractor={(rowView) => String(rowView.id)}
      renderSectionHeader={({ section }) => (section.title === undefined ? null : <SbSectionHeader title={section.title} />)}
      renderItem={({ item: rowView, index: rowIndex }) => (
        <SbListEntrance itemIndex={rowIndex}>
          <NotificationInboxRow
            rowView={rowView}
            onPress={() =>
              router.push(kind === 'NOTICE' ? toNoticeDetailRoute(String(rowView.id)) : toNotificationDetailRoute(String(rowView.id)))
            }
          />
        </SbListEntrance>
      )}
      stickySectionHeadersEnabled={false}
      ListEmptyComponent={renderListState()}
      ListFooterComponent={renderFooter()}
      onEndReached={onEndReached}
      contentContainerStyle={styles.content}
      refreshControl={createSbRefreshControl({ colors, isRefreshing: isPullRefreshing, onRefresh: onPullRefresh })}
    />
  );

  /** 글이 없을 때: 로딩 뼈대 / 오류 / 빈 상태 */
  function renderListState() {
    if (notificationPagesQuery.isPending || openedBaseline === undefined) return <SbLoadingSkeleton variant="listRows" />;
    if (notificationPagesQuery.isError) {
      const failure = toAppFailure(notificationPagesQuery.error);
      return <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => notificationPagesQuery.refetch()} />;
    }
    return <SbEmptyState title={texts.emptyTitle} description={texts.emptyDescription} iconName={texts.emptyIconName} />;
  }

  /** 목록 끝: 다음 페이지 뼈대 / 마지막이면 보관 기간 안내 */
  function renderFooter() {
    if (notificationPagesQuery.isFetchingNextPage) return <SbLoadingSkeleton variant="listRows" rowCount={NEXT_PAGE_SKELETON_ROW_COUNT} />;
    if (!isAtEnd || texts.endNote === undefined) return undefined;
    return (
      <View style={styles.endNote}>
        <SbText variant="caption" color={colors.tertiaryLabel} isCentered>
          {texts.endNote}
        </SbText>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  // 빈 상태·오류가 남은 높이 가운데에 오도록, 마지막 줄이 화면 아래 끝에 붙지 않게
  content: { flexGrow: 1, paddingBottom: sbSpacing.extraLarge },
  endNote: { paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.large },
});
