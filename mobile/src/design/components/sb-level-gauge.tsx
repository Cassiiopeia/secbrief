import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbAlertLevelTone, type SbColors, sbSize, sbSpacing } from '../tokens';
import { SbText } from './sb-text';

/** 단계 칸 1개: 색조 + 아래 이름 (작은 막대는 이름 없음) */
export interface SbLevelGaugeSegment {
  tone: SbAlertLevelTone;
  label?: string;
}

interface SbLevelGaugeProps {
  /** 낮은 단계부터 */
  segments: SbLevelGaugeSegment[];
  /** 지금 단계 위치 (1부터, 0 이면 모름: 전부 흐리게) */
  currentStep: number;
  /** large 경보단계 화면 머리 (지금 칸이 두껍고 이름 표시), compact 해외 지표 줄 */
  size: 'large' | 'compact';
  accessibilityLabel: string;
}

// 지금 단계보다 높은 칸의 흐림 정도 (색은 알아보되 지금 단계가 먼저 보이게)
const UPCOMING_SEGMENT_OPACITY = 0.28;

/** 경보 단계 막대: 단계마다 색 칸, 지금 단계까지 채우고 그 위는 흐리게 (사이버위기 경보 5단계, 해외 지표 4·5단계 공용) */
export function SbLevelGauge({ segments, currentStep, size, accessibilityLabel }: SbLevelGaugeProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const isLarge = size === 'large';
  return (
    <View style={styles.gauge} accessible accessibilityRole="progressbar" accessibilityLabel={accessibilityLabel}>
      {segments.map((segment, index) => {
        const step = index + 1;
        const isCurrent = step === currentStep;
        return (
          <View key={`${segment.tone}-${step}`} style={styles.segment}>
            <View
              style={[
                isLarge && isCurrent ? styles.currentBar : isLarge ? styles.bar : styles.compactBar,
                styles[segment.tone],
                step > currentStep && styles.upcoming,
              ]}
            />
            {isLarge && segment.label !== undefined && (
              <SbText variant={isCurrent ? 'button' : 'caption'} color={isCurrent ? colors.label : colors.tertiaryLabel} isCentered>
                {segment.label}
              </SbText>
            )}
          </View>
        );
      })}
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    gauge: { flexDirection: 'row', alignItems: 'flex-end', gap: sbSpacing.extraSmall },
    segment: { flex: 1, gap: sbSpacing.small },
    bar: { height: sbSize.levelGaugeBar, borderRadius: sbSize.levelGaugeBar },
    currentBar: { height: sbSize.levelGaugeCurrentBar, borderRadius: sbSize.levelGaugeCurrentBar },
    compactBar: { height: sbSize.levelGaugeCompactBar, borderRadius: sbSize.levelGaugeCompactBar },
    upcoming: { opacity: UPCOMING_SEGMENT_OPACITY },
    green: { backgroundColor: colors.alertLevelGreen },
    blue: { backgroundColor: colors.alertLevelBlue },
    yellow: { backgroundColor: colors.alertLevelYellow },
    orange: { backgroundColor: colors.alertLevelOrange },
    red: { backgroundColor: colors.alertLevelRed },
  });
