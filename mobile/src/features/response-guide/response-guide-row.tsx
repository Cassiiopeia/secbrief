import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import {
  type SbColors,
  SbArticleSecondaryLineText,
  SbBadgeRow,
  SbPressable,
  SbText,
  sbMaximumLines,
  sbSize,
  sbSpacing,
  toSbArticleTitleColor,
  useSbStyles,
  useSbTheme,
} from '@/design';

import { useArticleOpener } from '../articles/use-article-opener';
import { useReadArticleIds } from '../articles/use-read-articles';
import type { ResponseGuideRowView } from './response-guide-view';

/**
 * 대응 모아보기 1행 (기사 상세 구역·알림 상세 공식 대응): 출처 · 묶인 이유 > 제목 > (악용 중) > (CVSS·조치 기한) > (한 줄 결론) > 날짜 | 화살표
 * 누르면 앱 안 기사 상세 (원문은 그 화면의 원문 보기). 읽은 기사는 제목을 흐리게, 첫 행이 아니면 위 구분선
 */
export function ResponseGuideRow({ rowView, isFirst }: { rowView: ResponseGuideRowView; isFirst: boolean }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const openArticle = useArticleOpener();
  const isRead = useReadArticleIds().has(rowView.articleRowView.id);
  return (
    <View role="listitem">
      <SbPressable accessibilityLabel={rowView.accessibilityLabel} onPress={() => openArticle(rowView.articleRowView)} feedback="dim">
        <View style={[styles.row, !isFirst && styles.separator]}>
          <View style={styles.text}>
            <SbText variant="caption" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
              {rowView.kickerLabel}
            </SbText>
            <SbText variant="rowTitle" color={toSbArticleTitleColor(colors, isRead)} numberOfLines={sbMaximumLines.cardTitle}>
              {rowView.title}
            </SbText>
            {rowView.badges.length > 0 && <SbBadgeRow badges={rowView.badges} />}
            {rowView.factLine !== undefined && (
              <SbText variant="factValue" color={colors.secondaryLabel}>
                {rowView.factLine}
              </SbText>
            )}
            {rowView.oneLine !== undefined && <SbArticleSecondaryLineText secondaryLine={{ text: rowView.oneLine, tone: 'summary' }} />}
            <SbText variant="timeLabel" color={colors.tertiaryLabel}>
              {rowView.dateLabel}
            </SbText>
          </View>
          <MaterialIcons name="chevron-right" size={sbSize.rowIcon} color={colors.tertiaryLabel} />
        </View>
      </SbPressable>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    row: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.compact,
      minHeight: sbSize.minimumTouchTarget,
      paddingVertical: sbSpacing.compact,
    },
    separator: { borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: colors.separator },
    text: { flex: 1, gap: sbSpacing.extraSmall },
  });
