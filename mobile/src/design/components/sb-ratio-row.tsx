import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withDelay, withTiming } from 'react-native-reanimated';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbFullWidthPercent, sbMaximumLines, sbMotion, sbRadius, sbSpacing } from '../tokens';
import { SbBadge } from './sb-badge';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

/** 증감 방향 4종: up 늘어남·상승, down 줄어듦·하락, flat 그대로, new 새로 나옴 */
export type SbChangeDirection = 'up' | 'down' | 'flat' | 'new';

/** 증감 표시: 방향 + 옆 글자 (예: 23%, 3, NEW). flat 은 줄표만 */
export interface SbRatioChange {
  direction: SbChangeDirection;
  label: string;
}

interface SbRatioRowProps {
  /** 순위 등 제목 앞 고정폭 글자 (예: 2) */
  leadingLabel?: string;
  title: string;
  /** 오른쪽 값 (예: 12건) */
  valueLabel: string;
  /** 증감 표시: 화살표 + 글자 (새로 나온 것은 NEW 배지) */
  change?: SbRatioChange;
  /** 막대 길이 비율 (0~1) */
  ratio: number;
  /** 등장 순번 (막대가 순서대로 자람) */
  order: number;
  accessibilityLabel: string;
  onPress?: () => void;
}

const BAR_HEIGHT = 6;
const CHANGE_ICON_SIZE = 16;
const CHANGE_ICON_NAME_BY_DIRECTION: Record<Exclude<SbChangeDirection, 'new'>, SbIconName> = {
  up: 'arrow-upward',
  down: 'arrow-downward',
  flat: 'remove',
};

/** 비율 행: (순위) 제목 + 증감 화살표 + 값 + 비율 막대. 막대는 0 에서 제 길이까지 자람 (동작 줄이기면 바로 제 길이) */
export function SbRatioRow({ leadingLabel, title, valueLabel, change, ratio, order, accessibilityLabel, onPress }: SbRatioRowProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const isMotionReduced = useIsMotionReduced();
  const shownRatio = useSharedValue(isMotionReduced ? ratio : 0);
  useEffect(() => {
    if (isMotionReduced) {
      shownRatio.set(ratio);
      return;
    }
    shownRatio.set(
      withDelay(
        order * sbMotion.entranceStaggerMilliseconds,
        withTiming(ratio, { duration: sbMotion.barGrowMilliseconds, easing: sbMotion.entranceEasing }),
      ),
    );
  }, [isMotionReduced, order, ratio, shownRatio]);
  const barStyle = useAnimatedStyle(() => ({ width: `${shownRatio.value * sbFullWidthPercent}%` }));
  // 늘어남은 강조색 쪽, 줄어듦·그대로는 흐린 글자색
  const changeColor = change?.direction === 'up' ? colors.urgent : colors.tertiaryLabel;
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress} feedback="dim">
      <View style={styles.row}>
        <View style={styles.content}>
          <View style={styles.text}>
            {leadingLabel !== undefined && (
              <SbText variant="capsLabel" color={colors.tertiaryLabel}>
                {leadingLabel}
              </SbText>
            )}
            <View style={styles.title}>
              <SbText variant="rowTitle" color={colors.label} numberOfLines={sbMaximumLines.singleLine}>
                {title}
              </SbText>
            </View>
            {change !== undefined && change.direction === 'new' && <SbBadge label={change.label} tone="notice" />}
            {change !== undefined && change.direction !== 'new' && (
              <View style={styles.change}>
                <MaterialIcons name={CHANGE_ICON_NAME_BY_DIRECTION[change.direction]} size={CHANGE_ICON_SIZE} color={changeColor} />
                {change.label !== '' && (
                  <SbText variant="caption" color={changeColor}>
                    {change.label}
                  </SbText>
                )}
              </View>
            )}
            <SbText variant="button" color={colors.label}>
              {valueLabel}
            </SbText>
          </View>
          <View style={styles.track}>
            <Animated.View style={[styles.bar, barStyle]} />
          </View>
        </View>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    row: { paddingHorizontal: sbSpacing.pageHorizontal },
    content: {
      gap: sbSpacing.small,
      paddingVertical: sbSpacing.rowVertical,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: colors.separator,
    },
    text: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.small },
    title: { flex: 1 },
    change: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.hairline },
    track: { height: BAR_HEIGHT, borderRadius: sbRadius.pill, backgroundColor: colors.surfaceRaised, overflow: 'hidden' },
    bar: { height: BAR_HEIGHT, borderRadius: sbRadius.pill, backgroundColor: colors.feature },
  });
