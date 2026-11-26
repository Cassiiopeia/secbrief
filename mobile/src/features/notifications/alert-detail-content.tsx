import { StyleSheet, View } from 'react-native';

import type { AppNotificationDetail } from '@/data/models/app-notification';
import {
  SbBadgeRow,
  SbBriefCard,
  SbEmptyState,
  SbEntrance,
  SbFilledButton,
  SbNoticeMark,
  SbNumberedList,
  SbSectionHeader,
  SbText,
  sbSpacing,
  useSbTheme,
} from '@/design';

import { AI_SUMMARY_NOTICE_LABEL, BRIEFING_SECTION_TITLES } from '../article-detail/article-briefing-view';
import { ArticleFeedItem } from '../articles/article-feed-item';
import { useArticleOpener } from '../articles/use-article-opener';
import { ResponseGuideRow } from '../response-guide/response-guide-row';
import { RESPONSE_GUIDE_SECTION_TEXTS } from '../response-guide/response-guide-texts';
import { type AlertArticleEntryView, type AlertDetailBlock, toAlertDetailView } from './notification-detail-view';

// 등장 순번: 머리 > 구역들 (구역은 머리 다음부터 차례로)
const HEADING_ORDER = 0;
const FIRST_BLOCK_ORDER = 1;
// 기사마다 붙는 대응 안내 카드 제목
const ALERT_GUIDE_CARD_TITLE = '대응 안내';

/**
 * 긴급 알림 상세 본문: 머리(배지 > 제목 > 보낸 시각 > 요약 한 줄) > 구역들
 * 구역 = 왜 알렸는지(종류별 이유) > 알린 기사마다 기사 행(누르면 기사 상세) + 대응 안내 카드
 */
export function AlertDetailContent({ detail }: { detail: AppNotificationDetail }) {
  const { colors } = useSbTheme();
  const detailView = toAlertDetailView(detail, new Date());
  return (
    <>
      <SbEntrance order={HEADING_ORDER}>
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
      </SbEntrance>
      {detailView.blocks.length === 0 ? (
        <SbEmptyState title="알림에 담긴 소식을 찾지 못했어요" description="소식 탭에서 최신 소식을 볼 수 있어요." />
      ) : (
        detailView.blocks.map((block, blockIndex) => (
          <SbEntrance key={block.key} order={FIRST_BLOCK_ORDER + blockIndex}>
            <AlertDetailBlockView block={block} />
          </SbEntrance>
        ))
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
          {block.entries.map((entry) => (
            <AlertArticleEntry key={entry.key} entry={entry} />
          ))}
        </View>
      );
  }
}

/**
 * 알린 기사 1건: 기사 행 > 대응 안내 카드 (이렇게 대응해요(AI) > 공식 대응 첫 건 > 대응 모아보기 N건 보기, 없으면 원문 안내)
 * 버튼은 그 기사 상세를 대응 모아보기 위치로 열어 줌
 */
function AlertArticleEntry({ entry }: { entry: AlertArticleEntryView }) {
  const { colors } = useSbTheme();
  const openArticle = useArticleOpener();
  const { responseGuideButtonLabel } = entry;
  return (
    <View style={styles.entry}>
      <ArticleFeedItem rowView={entry.rowView} presentation="compact" />
      <SbBriefCard title={ALERT_GUIDE_CARD_TITLE} iconName="verified-user">
        {entry.actions.length > 0 && (
          <View style={styles.guidePart}>
            <View style={styles.guidePartHeader}>
              <View style={styles.guidePartTitle} accessibilityRole="header">
                <SbText variant="rowTitle" color={colors.label}>
                  {BRIEFING_SECTION_TITLES.actions}
                </SbText>
              </View>
              <SbNoticeMark label={AI_SUMMARY_NOTICE_LABEL} color={colors.tertiaryLabel} />
            </View>
            <SbNumberedList items={entry.actions} />
          </View>
        )}
        {entry.leadOfficialRow !== undefined && (
          <View style={styles.guidePart}>
            <View accessibilityRole="header">
              <SbText variant="rowTitle" color={colors.label}>
                {RESPONSE_GUIDE_SECTION_TEXTS.OFFICIAL.title}
              </SbText>
            </View>
            <View role="list">
              <ResponseGuideRow rowView={entry.leadOfficialRow} isFirst />
            </View>
          </View>
        )}
        {responseGuideButtonLabel !== undefined && (
          <SbFilledButton
            label={responseGuideButtonLabel}
            accessibilityLabel={entry.responseGuideButtonAccessibilityLabel}
            trailingIconName="chevron-right"
            tone="quiet"
            onPress={() => openArticle(entry.rowView, 'responseGuide')}
          />
        )}
        {entry.emptyGuideMessage !== undefined && (
          <SbText variant="body" color={colors.tertiaryLabel}>
            {entry.emptyGuideMessage}
          </SbText>
        )}
      </SbBriefCard>
    </View>
  );
}

const styles = StyleSheet.create({
  heading: { gap: sbSpacing.small, paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.small },
  entry: { gap: sbSpacing.extraSmall },
  guidePart: { gap: sbSpacing.small },
  guidePartHeader: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.small },
  guidePartTitle: { flex: 1 },
});
