import type { InfiniteData, UseInfiniteQueryResult } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { FlatList, StyleSheet } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import type { ArticlePage } from '@/data/models/article-page';
import type { ArticleKind } from '@/data/models/briefing-item';
import {
  SbEmptyState,
  SbErrorState,
  SbListEntrance,
  SbLoadingSkeleton,
  type SbLoadingSkeletonVariant,
  createSbRefreshControl,
  sbSpacing,
  useSbTheme,
} from '@/design';

import { ArticleFeedItem } from './article-feed-item';
import { type ArticleListPurpose, toArticleFeedPresentation } from './article-feed-layout';
import { toArticleRowView } from './article-row-view';

interface ArticleInfiniteListProps {
  articlePagesQuery: UseInfiniteQueryResult<InfiniteData<ArticlePage>>;
  /** 목록 위에 붙는 머리 (머리글, 필터 칩, 다른 묶음) */
  listHeader?: ReactElement;
  /** feed 훑어보는 피드(중요도별 카드·행), lookup 찾는 목록(조밀한 행만) */
  listPurpose: ArticleListPurpose;
  /** 피드의 맨 위 한 건을 대표 자리(큰 카드)로 보여 줄지 */
  hasLeadCard?: boolean;
  /** 목록 자리만 봐도 아는 종류 (그 종류 배지는 생략) */
  impliedKind?: ArticleKind;
  /** 로딩 뼈대 배치 (썸네일 있는 목록 articleRows, 글만 있는 목록 listRows) */
  skeletonVariant: SbLoadingSkeletonVariant;
  emptyTitle: string;
  emptyDescription: string;
  /** 빈 상태에서 다른 보기로 가는 버튼 (예: 전체 보기) */
  emptyAction?: { label: string; onPress: () => void };
  isPullRefreshing: boolean;
  onPullRefresh: () => void;
}

// 다음 페이지를 불러오는 동안 목록 끝에 보이는 뼈대 행 수
const NEXT_PAGE_SKELETON_ROW_COUNT = 2;

/**
 * 소식 무한 목록: 머리 > 소식들 (피드는 중요도에 따라 큰 카드·중간 카드·조밀한 행, 찾는 목록은 조밀한 행. 끝에 닿으면 다음 페이지) > 상태(로딩·오류·빈 상태)
 * 오류는 목록 자리에만 보여 머리 쪽 묶음은 그대로 남음
 */
export function ArticleInfiniteList({
  articlePagesQuery,
  listHeader,
  listPurpose,
  hasLeadCard = false,
  impliedKind,
  skeletonVariant,
  emptyTitle,
  emptyDescription,
  emptyAction,
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
      renderItem={({ item: rowView, index: rowIndex }) => (
        <SbListEntrance itemIndex={rowIndex}>
          <ArticleFeedItem
            rowView={rowView}
            presentation={toArticleFeedPresentation(
              { importance: rowView.importance, publishedAt: rowView.publishedAt, listPurpose, isLead: hasLeadCard && rowIndex === 0 },
              now,
            )}
            impliedKind={impliedKind}
          />
        </SbListEntrance>
      )}
      ListHeaderComponent={listHeader}
      ListEmptyComponent={renderListState()}
      ListFooterComponent={
        articlePagesQuery.isFetchingNextPage ? (
          <SbLoadingSkeleton variant={skeletonVariant} rowCount={NEXT_PAGE_SKELETON_ROW_COUNT} />
        ) : undefined
      }
      onEndReached={onEndReached}
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={styles.content}
      refreshControl={createSbRefreshControl({ colors, isRefreshing: isPullRefreshing, onRefresh: onPullRefresh })}
    />
  );

  /** 소식이 없을 때: 로딩 뼈대 / 오류 / 빈 상태 */
  function renderListState() {
    if (articlePagesQuery.isPending) return <SbLoadingSkeleton variant={skeletonVariant} />;
    if (articlePagesQuery.isError) {
      const failure = toAppFailure(articlePagesQuery.error);
      return (
        <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => articlePagesQuery.refetch()} />
      );
    }
    return (
      <SbEmptyState
        illustration="feedEmpty"
        title={emptyTitle}
        description={emptyDescription}
        actionLabel={emptyAction?.label}
        onActionPress={emptyAction?.onPress}
      />
    );
  }
}

const styles = StyleSheet.create({
  // 빈 상태·오류가 남은 높이 가운데에 오도록, 마지막 소식이 탭바에 붙지 않게
  content: { flexGrow: 1, paddingBottom: sbSpacing.large },
});
