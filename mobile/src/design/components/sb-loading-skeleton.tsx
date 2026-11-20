import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withRepeat, withTiming } from 'react-native-reanimated';

import { useSbStyles } from '../theme/use-sb-theme';
import { type SbColors, sbMotion, sbRadius, sbSpacing } from '../tokens';

// 뼈대 블록 높이 (긴급 타일, 통계 타일, 스트림 한 줄), 깜박임의 가장 옅은 불투명도
const URGENT_BLOCK_HEIGHT = 220;
const STAT_BLOCK_HEIGHT = 104;
const STREAM_LINE_HEIGHT = 44;
const STREAM_LINE_COUNT = 3;
const DIMMEST_OPACITY = 0.45;

/** 로딩 뼈대: 홈 배치와 같은 자리에 회색 블록이 은은하게 깜박임 */
export function SbLoadingSkeleton() {
  const styles = useSbStyles(createStyles);
  const opacity = useSharedValue(1);
  useEffect(() => {
    opacity.set(withRepeat(withTiming(DIMMEST_OPACITY, { duration: sbMotion.skeletonMilliseconds }), -1, true));
  }, [opacity]);
  const animatedStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));
  return (
    <Animated.View style={[styles.wrap, animatedStyle]} accessibilityLabel="불러오는 중이에요">
      <View style={[styles.block, styles.urgent]} />
      <View style={styles.row}>
        <View style={[styles.block, styles.stat]} />
        <View style={[styles.block, styles.stat]} />
      </View>
      {Array.from({ length: STREAM_LINE_COUNT }, (_, lineIndex) => (
        <View key={lineIndex} style={[styles.block, styles.line]} />
      ))}
    </Animated.View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { paddingHorizontal: sbSpacing.tileHorizontal, gap: sbSpacing.tileGap },
    row: { flexDirection: 'row', gap: sbSpacing.tileGap },
    block: { backgroundColor: colors.surfaceRaised, borderRadius: sbRadius.tile },
    urgent: { height: URGENT_BLOCK_HEIGHT },
    stat: { flex: 1, height: STAT_BLOCK_HEIGHT },
    line: { height: STREAM_LINE_HEIGHT, borderRadius: sbRadius.thumbnail },
  });
