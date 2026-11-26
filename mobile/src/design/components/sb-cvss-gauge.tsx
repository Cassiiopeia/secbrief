import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import {
  type SbColors,
  type SbSeverityTone,
  sbFullWidthPercent,
  sbMaximumLines,
  sbMotion,
  sbRadius,
  sbSize,
  sbSpacing,
  toSbSeverityColor,
} from '../tokens';
import { SbText } from './sb-text';

/** CVSS 게이지에 보일 값 (점수는 원문 값 그대로) */
export interface SbCvssGaugeValue {
  /** 예: 9.8 */
  scoreLabel: string;
  /** 점수 아래 작은 글자 (예: CVSS 3.1) */
  caption: string;
  /** 막대 길이 비율 (0~1, 10점 만점 대비) */
  ratio: number;
  tone: SbSeverityTone;
}

interface SbCvssGaugeProps {
  value: SbCvssGaugeValue;
  /** 그림 위에 얹을 때: 표면색 바탕 상자로 감쌈 */
  isBoxed?: boolean;
}

const BAR_HEIGHT = 4;

/** CVSS 게이지: 심각도 색 점수 + 10점 대비 막대 + 버전 표기. 막대는 0 에서 자람 (동작 줄이기면 바로 제 길이) */
export function SbCvssGauge({ value, isBoxed = false }: SbCvssGaugeProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const isMotionReduced = useIsMotionReduced();
  const severityColor = toSbSeverityColor(colors, value.tone);
  const shownRatio = useSharedValue(isMotionReduced ? value.ratio : 0);
  useEffect(() => {
    shownRatio.set(
      isMotionReduced
        ? value.ratio
        : withTiming(value.ratio, { duration: sbMotion.barGrowMilliseconds, easing: sbMotion.entranceEasing }),
    );
  }, [isMotionReduced, shownRatio, value.ratio]);
  const barStyle = useAnimatedStyle(() => ({ width: `${shownRatio.value * sbFullWidthPercent}%` }));
  return (
    <View style={[styles.gauge, isBoxed && styles.boxed]}>
      <SbText variant="gaugeValue" color={severityColor}>
        {value.scoreLabel}
      </SbText>
      <View style={styles.track}>
        <Animated.View style={[styles.bar, styles[value.tone], barStyle]} />
      </View>
      <SbText variant="capsLabel" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
        {value.caption}
      </SbText>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    gauge: { width: sbSize.cvssGauge, gap: sbSpacing.hairline },
    boxed: {
      width: sbSize.cvssGauge + sbSpacing.small * 2,
      padding: sbSpacing.small,
      borderRadius: sbRadius.button,
      backgroundColor: colors.surface,
    },
    track: { height: BAR_HEIGHT, borderRadius: sbRadius.pill, backgroundColor: colors.surfaceRaised, overflow: 'hidden' },
    bar: { height: BAR_HEIGHT, borderRadius: sbRadius.pill },
    critical: { backgroundColor: colors.severityCritical },
    high: { backgroundColor: colors.severityHigh },
    medium: { backgroundColor: colors.severityMedium },
    low: { backgroundColor: colors.severityLow },
  });
