import type { InfiniteData, UseInfiniteQueryResult } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { FlatList, RefreshControl, StyleSheet } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import type { ArticlePage } from '@/data/models/article-page';
import { SbArticleRow, SbEmptyState, SbErrorState, SbLoadingSkeleton, useSbTheme } from '@/design';

import { toArticleRowView } from './article-row-view';
import { openOriginalArticle } from './open-original-article';

interface ArticleInfiniteListProps {
  articlePagesQuery: UseInfiniteQueryResult<InfiniteData<ArticlePage>>;
  /** 목록 위에 붙는 머리 (머리글, 필터 칩, 다른 묶음) */
  listHeader: ReactElement;
  emptyTitle: string;
  emptyDescription: string;
  isPullRefreshing: boolean;
  onPullRefresh: () => void;
}

/**
 * 소식 무한 목록: 머리 > 행들 (끝에 닿으면 다음 페이지) > 상태(로딩·오류·빈 상태)
 * 오류는 목록 자리에만 보여 머리 쪽 묶음은 그대로 남음
 */
export function ArticleInfiniteList({
  articlePagesQuery,
  listHeader,
  emptyTitle,
  emptyDescription,
  isPullRefreshing,
  onPullRefresh,
}: ArticleInfiniteListProps) {
  const { colors } = useSbTheme();
  const now = new Date();
  const rowViews = (articlePagesQuery.data?.pages ?? [])
    .flatMap((page) => page.items)
    .map((item) => toArticleRowView(item, now));

  const onEndReached = () => {
    // 이미 불러오는 중이면 다시 부르지 않음
    if (articlePagesQuery.hasNextPage && !articlePagesQuery.isFetchingNextPage) {
      void articlePagesQuery.fetchNextPage();
    }
  };

  return (
    <FlatList
      data={rowViews}
      keyExtractor={(rowView) => rowView.id}
      renderItem={({ item: rowView }) => (
        <SbArticleRow
          title={rowView.title}
          originalTitle={rowView.originalTitle}
          translationNoticeLabel={rowView.translationNoticeLabel}
          metaLabel={rowView.metaLabel}
          badgeLabels={rowView.badgeLabels}
          accessibilityLabel={rowView.accessibilityLabel}
          onPress={() => openOriginalArticle(rowView.originalUrl)}
        />
      )}
      ListHeaderComponent={listHeader}
      ListEmptyComponent={renderListState()}
      onEndReached={onEndReached}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={isPullRefreshing} onRefresh={onPullRefresh} tintColor={colors.label} />}
    />
  );

  /** 행이 없을 때: 로딩 뼈대 / 오류 / 빈 상태 */
  function renderListState() {
    if (articlePagesQuery.isPending) return <SbLoadingSkeleton />;
    if (articlePagesQuery.isError) {
      const failure = toAppFailure(articlePagesQuery.error);
      return (
        <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => articlePagesQuery.refetch()} />
      );
    }
    return <SbEmptyState title={emptyTitle} description={emptyDescription} />;
  }
}

const styles = StyleSheet.create({
  // 빈 상태·오류가 남은 높이 가운데에 오도록
  content: { flexGrow: 1 },
});
