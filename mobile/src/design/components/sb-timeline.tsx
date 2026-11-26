import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSpacing, toSbArticleTitleColor } from '../tokens';
import { SbCapsLabel } from './sb-caps-label';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

/** 타임라인: 왼쪽 세로선 + 날짜 묶음들 (위가 최신) */
export function SbTimeline({ children }: { children: ReactNode }) {
  const styles = useSbStyles(createStyles);
  return <View style={styles.timeline}>{children}</View>;
}

interface SbTimelineGroupProps {
  /** 날짜 머리 (예: 11.26 THU) */
  dateLabel: string;
  children: ReactNode;
}

/** 타임라인 날짜 묶음: 세로선 위 점 + 날짜 머리 + 그날의 항목들 */
export function SbTimelineGroup({ dateLabel, children }: SbTimelineGroupProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.group}>
      <View style={styles.dot} />
      <SbCapsLabel text={dateLabel} color={colors.label} />
      {children}
    </View>
  );
}

interface SbTimelineItemProps {
  title: string;
  /** 게시자 등 작은 한 줄 */
  metaLabel: string;
  isRead?: boolean;
  accessibilityLabel: string;
  onPress: () => void;
}

const DOT_SIZE = 9;
const LINE_WIDTH = 2;

/** 타임라인 항목: 표면색 상자에 제목 2줄 + 작은 한 줄 */
export function SbTimelineItem({ title, metaLabel, isRead = false, accessibilityLabel, onPress }: SbTimelineItemProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress}>
      <View style={styles.item}>
        <SbText variant="streamTitle" color={toSbArticleTitleColor(colors, isRead)} numberOfLines={sbMaximumLines.compactTitle}>
          {title}
        </SbText>
        <SbText variant="timeLabel" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
          {metaLabel}
        </SbText>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    timeline: {
      marginHorizontal: sbSpacing.pageHorizontal,
      paddingLeft: sbSpacing.medium,
      borderLeftWidth: LINE_WIDTH,
      borderLeftColor: colors.separator,
      gap: sbSpacing.large,
    },
    group: { gap: sbSpacing.small },
    // 점: 세로선 가운데에 얹힘 (들여쓰기 + 선 굵기 절반만큼 왼쪽으로)
    dot: {
      position: 'absolute',
      left: -(sbSpacing.medium + (DOT_SIZE + LINE_WIDTH) / 2),
      top: sbSpacing.extraSmall,
      width: DOT_SIZE,
      height: DOT_SIZE,
      borderRadius: sbRadius.pill,
      backgroundColor: colors.urgent,
    },
    item: { gap: sbSpacing.extraSmall, padding: sbSpacing.compact, borderRadius: sbRadius.thumbnail, backgroundColor: colors.surface },
  });
