import { useQueryClient } from '@tanstack/react-query';
import { router } from 'expo-router';
import { SectionList, StyleSheet } from 'react-native';

import type { InboxNotificationKind, AppNotificationSummary } from '@/data/models/app-notification';
import { SbListEntrance, SbSectionHeader, createSbRefreshControl, sbSpacing, useSbTheme } from '@/design';

import { usePullRefresh } from '../articles/use-pull-refresh';
import { toNoticeDetailRoute, toNotificationDetailRoute } from '../navigation/app-routes';
import { NotificationInboxListFooter, NotificationInboxListState } from './notification-inbox-list-states';
import { NotificationInboxRow } from './notification-inbox-row';
import { toNotificationInboxSections } from './notification-inbox-view';
import { useInboxRowViews } from './use-inbox-row-views';
import { useNotificationPages } from './use-notification-pages';
import { LATEST_NOTIFICATION_IDS_QUERY_KEY } from './use-unseen-notification-dots';

/**
 * 알림함 한 종류의 목록: 날짜 묶음(긴급 알림) 또는 고정 먼저(공지사항) > 끝에 닿으면 다음 페이지 > 상태(로딩·오류·빈 상태)
 * 새 글 기준은 화면을 연 순간의 본 번호로 고정 (목록을 보는 동안 강조가 사라지지 않게), 받은 글은 곧바로 본 것으로 기록
 */
export function NotificationInboxList({ kind }: { kind: InboxNotificationKind }) {
  const { colors } = useSbTheme();
  const queryClient = useQueryClient();
  const notificationPagesQuery = useNotificationPages(kind);
  const { isPullRefreshing, onPullRefresh } = usePullRefresh(() =>
    Promise.all([notificationPagesQuery.refetch(), queryClient.invalidateQueries({ queryKey: LATEST_NOTIFICATION_IDS_QUERY_KEY })]),
  );

  const summaries: AppNotificationSummary[] = (notificationPagesQuery.data?.pages ?? []).flatMap((page) => page.items);
  const { rowViews, now, isBaselineReady } = useInboxRowViews(kind, summaries);
  const sections = toNotificationInboxSections(kind, summaries, rowViews, now);

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
      ListEmptyComponent={
        <NotificationInboxListState
          kind={kind}
          isLoading={notificationPagesQuery.isPending || !isBaselineReady}
          error={notificationPagesQuery.isError ? notificationPagesQuery.error : undefined}
          onRetry={() => void notificationPagesQuery.refetch()}
        />
      }
      ListFooterComponent={
        <NotificationInboxListFooter
          kind={kind}
          isFetchingNextPage={notificationPagesQuery.isFetchingNextPage}
          isAtEnd={!notificationPagesQuery.hasNextPage && rowViews.length > 0}
        />
      }
      onEndReached={onEndReached}
      contentContainerStyle={styles.content}
      refreshControl={createSbRefreshControl({ colors, isRefreshing: isPullRefreshing, onRefresh: onPullRefresh })}
    />
  );
}

const styles = StyleSheet.create({
  // 빈 상태·오류가 남은 높이 가운데에 오도록, 마지막 줄이 화면 아래 끝에 붙지 않게
  content: { flexGrow: 1, paddingBottom: sbSpacing.extraLarge },
});
