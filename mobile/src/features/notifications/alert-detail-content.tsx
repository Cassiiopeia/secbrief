import { StyleSheet, View } from 'react-native';

import type { AppNotificationDetail } from '@/data/models/app-notification';
import { SbBadgeRow, SbEmptyState, SbSectionHeader, SbText, sbSpacing, useSbTheme } from '@/design';

import { ArticleFeedItem } from '../articles/article-feed-item';
import { type AlertDetailBlock, toAlertDetailView } from './notification-detail-view';

/** 긴급 알림 상세 본문: 머리(배지 > 제목 > 보낸 시각 > 요약 한 줄) > 구역들 (종류별 이유 + 기사 행, 누르면 기사 상세) */
export function AlertDetailContent({ detail }: { detail: AppNotificationDetail }) {
  const { colors } = useSbTheme();
  const detailView = toAlertDetailView(detail, new Date());
  return (
    <>
      <View style={styles.heading}>
        <SbBadgeRow badges={detailView.badges} />
        <View accessibilityRole="header">
          <SbText variant="detailTitle" color={colors.label}>
            {detailView.title}
          </SbText>
        </View>
        <SbText variant="timeLabel" color={colors.tertiaryLabel}>
          {detailView.timeLabel}
        </SbText>
        {detailView.summaryLine !== undefined && (
          <SbText variant="body" color={colors.secondaryLabel}>
            {detailView.summaryLine}
          </SbText>
        )}
      </View>
      {detailView.blocks.length === 0 ? (
        <SbEmptyState title="알림에 담긴 소식을 찾지 못했어요" description="소식 탭에서 최신 소식을 볼 수 있어요." />
      ) : (
        detailView.blocks.map((block) => <AlertDetailBlockView key={block.key} block={block} />)
      )}
    </>
  );
}

/** 구역 하나 그리기 (kind 별, 새 구역은 여기에 분기를 더함) */
function AlertDetailBlockView({ block }: { block: AlertDetailBlock }) {
  switch (block.kind) {
    case 'categoryArticles':
      return (
        <View>
          <SbSectionHeader title={block.title} caption={block.reason} />
          {block.rowViews.map((rowView) => (
            <ArticleFeedItem key={rowView.id} rowView={rowView} presentation="compact" />
          ))}
        </View>
      );
  }
}

const styles = StyleSheet.create({
  heading: { gap: sbSpacing.small, paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.small },
});
