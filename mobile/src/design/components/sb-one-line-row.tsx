import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, type SbSeverityTone, sbMaximumLines, sbRadius, sbSize, sbSpacing, toSbSeverityColor } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbOneLineRowProps {
  /** 앞 굵은 이름 (예: 패키지 이름) */
  leadingLabel?: string;
  title: string;
  /** 오른쪽 작은 글자 (예: PyPI · 11.20) */
  trailingLabel: string;
  /** 심각도 점 (없으면 회색 점) */
  severityTone?: SbSeverityTone;
  isRead?: boolean;
  accessibilityLabel: string;
  onPress: () => void;
}

/** 한 줄 행 (#93): 심각도 점 > 굵은 이름 > 제목(말줄임) > 오른쪽 작은 글자 + 아래 구분선. 덜 중요한 소식을 짧게 모을 때 */
export function SbOneLineRow({ leadingLabel, title, trailingLabel, severityTone, isRead = false, accessibilityLabel, onPress }: SbOneLineRowProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress} feedback="dim">
      <View style={styles.row}>
        <View style={styles.content}>
          <View style={[styles.dot, styles[severityTone ?? 'unknown']]} />
          <View style={styles.text}>
            <SbText variant="body" color={isRead ? colors.readLabel : colors.secondaryLabel} numberOfLines={sbMaximumLines.singleLine}>
              {leadingLabel === undefined ? title : `${leadingLabel}  ${title}`}
            </SbText>
          </View>
          <SbText variant="timeLabel" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
            {trailingLabel}
          </SbText>
        </View>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    row: { paddingHorizontal: sbSpacing.pageHorizontal },
    content: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.small,
      minHeight: sbSize.minimumTouchTarget,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: colors.separator,
    },
    dot: { width: sbSize.unreadDot, height: sbSize.unreadDot, borderRadius: sbRadius.pill },
    // 심각도 4단계 + 점수 없음(회색)
    critical: { backgroundColor: toSbSeverityColor(colors, 'critical') },
    high: { backgroundColor: toSbSeverityColor(colors, 'high') },
    medium: { backgroundColor: toSbSeverityColor(colors, 'medium') },
    low: { backgroundColor: toSbSeverityColor(colors, 'low') },
    unknown: { backgroundColor: colors.tertiaryLabel },
    text: { flex: 1 },
  });
