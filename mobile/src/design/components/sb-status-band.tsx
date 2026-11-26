import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbCapsLabel } from './sb-caps-label';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbStatusBandProps {
  iconName: SbIconName;
  /** 아이콘 색 (없으면 긴급 색, 경보단계 띠는 단계 색) */
  iconColor?: string;
  /** 작은 라벨 (예: 사이버위기 경보) */
  capsLabel: string;
  /** 본문 한두 줄 (예: 주의 · 10.22부터) */
  title: string;
  accessibilityLabel: string;
  onPress: () => void;
}

/** 상태 띠: 아이콘 + 작은 라벨 + 한두 줄 글 + 화살표. 화면 위쪽에 얇게 놓이는 알림성 한 줄 */
export function SbStatusBand({ iconName, iconColor, capsLabel, title, accessibilityLabel, onPress }: SbStatusBandProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress}>
      <View style={styles.wrap}>
        <View style={styles.band}>
          <MaterialIcons name={iconName} size={sbSize.rowIcon} color={iconColor ?? colors.urgent} />
          <View style={styles.text}>
            <SbCapsLabel text={capsLabel} color={colors.tertiaryLabel} />
            <SbText variant="rowTitle" color={colors.label} numberOfLines={sbMaximumLines.compactTitle}>
              {title}
            </SbText>
          </View>
          <MaterialIcons name="chevron-right" size={sbSize.rowIcon} color={colors.tertiaryLabel} />
        </View>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { paddingHorizontal: sbSpacing.tileHorizontal, paddingBottom: sbSpacing.tileGap },
    band: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.compact,
      paddingHorizontal: sbSpacing.medium,
      paddingVertical: sbSpacing.compact,
      borderRadius: sbRadius.tile,
      backgroundColor: colors.surface,
    },
    text: { flex: 1, gap: sbSpacing.hairline },
  });
