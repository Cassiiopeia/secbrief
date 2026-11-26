import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle } from 'react-native-reanimated';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing, toSbArticleTitleColor } from '../tokens';
import { SbArticleImage } from './sb-article-image';
import type { SbCoverKind, SbCoverTone } from './sb-cover-art';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';
import { useSbPulse } from './use-sb-pulse';

/** 스트림 레일: 왼쪽 세로선 + 시간순 항목들 (위가 최신) */
export function SbStreamRail({ children }: { children: ReactNode }) {
  const styles = useSbStyles(createStyles);
  return <View style={styles.rail}>{children}</View>;
}

interface SbStreamRailItemProps {
  /** 예: 'KISA 보호나라 · 2시간 전' (영문은 대문자로 보여 줌) */
  timeLabel: string;
  title: string;
  /** 없으면 썸네일 없이 글만 */
  thumbnailKind?: SbCoverKind;
  thumbnailTone?: SbCoverTone;
  /** 소식 대표 이미지 주소 (있으면 커버 그림 위에 겹쳐 보임) */
  thumbnailImageUrl?: string;
  /** 가장 새 항목: 레일 점이 맥박처럼 퍼짐 */
  isLatest?: boolean;
  /** 이미 읽은 소식 (제목을 흐리게) */
  isRead?: boolean;
  onPress?: () => void;
}

const DOT_SIZE = 7;
const DOT_TOP = 5;
// 레일 선 굵기 (점을 선 가운데에 올리는 계산용)
const RAIL_LINE_WIDTH = 1;
// 맥박 고리: 가장 커졌을 때 배율, 쉬는 상태 불투명도
const LATEST_RING_SCALE = 3.6;
const LATEST_RING_OPACITY = 0.45;

/** 스트림 항목 한 건: 레일 점 + 게시자·날짜 라벨 + 제목(3줄까지) + (선택) 썸네일 */
export function SbStreamRailItem({
  timeLabel,
  title,
  thumbnailKind,
  thumbnailTone = 'neutral',
  thumbnailImageUrl,
  isLatest = false,
  isRead = false,
  onPress,
}: SbStreamRailItemProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const pulse = useSbPulse(isLatest);
  const ringStyle = useAnimatedStyle(() => ({
    opacity: pulse.value === 0 ? 0 : LATEST_RING_OPACITY * (1 - pulse.value),
    transform: [{ scale: 1 + (LATEST_RING_SCALE - 1) * pulse.value }],
  }));
  return (
    <SbPressable accessibilityLabel={`${title}, ${timeLabel}`} onPress={onPress} feedback="dim">
      <View style={styles.item}>
        <Animated.View style={[styles.dot, ringStyle]} />
        <View style={styles.dot} />
        <View style={styles.text}>
          <SbText variant="timeLabel" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
            {timeLabel.toUpperCase()}
          </SbText>
          <SbText variant="streamTitle" color={toSbArticleTitleColor(colors, isRead)} numberOfLines={sbMaximumLines.cardTitle}>
            {title}
          </SbText>
        </View>
        {thumbnailKind !== undefined && (
          <View style={styles.thumbnail}>
            <SbArticleImage imageUrl={thumbnailImageUrl} coverKind={thumbnailKind} coverTone={thumbnailTone} />
          </View>
        )}
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    rail: {
      paddingLeft: sbSpacing.railIndent,
      borderLeftWidth: RAIL_LINE_WIDTH,
      borderLeftColor: colors.tertiaryLabel,
      gap: sbSpacing.railItemGap,
    },
    item: { flexDirection: 'row', alignItems: 'flex-start', gap: sbSpacing.compact },
    // 레일 점: 세로선 위에 얹힘 (선 가운데 = 들여쓰기 + 선 굵기 절반)
    dot: {
      position: 'absolute',
      left: -(sbSpacing.railIndent + (DOT_SIZE + RAIL_LINE_WIDTH) / 2),
      top: DOT_TOP,
      width: DOT_SIZE,
      height: DOT_SIZE,
      borderRadius: sbRadius.pill,
      backgroundColor: colors.urgent,
    },
    text: { flex: 1, gap: sbSpacing.hairline },
    thumbnail: {
      width: sbSize.streamThumbnail,
      height: sbSize.streamThumbnail,
      borderRadius: sbRadius.thumbnail,
      overflow: 'hidden',
    },
  });
