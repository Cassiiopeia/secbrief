import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbFilledButtonProps {
  label: string;
  /** 글자 뒤 아이콘 (예: open-in-new) */
  trailingIconName?: SbIconName;
  /** 읽기 도우미 문구 (없으면 label) */
  accessibilityLabel?: string;
  /** accent 주 버튼(민트 바탕), quiet 곁들이는 버튼(회색 바탕: 주 버튼과 겨루지 않게) */
  tone?: 'accent' | 'quiet';
  onPress: () => void;
}

const ICON_SIZE = 18;

/** 채움 버튼 2종 (민트 바탕 주 버튼, 회색 바탕 곁들이는 버튼). 부모가 넓이를 주면 그만큼 채움 */
export function SbFilledButton({ label, trailingIconName, accessibilityLabel, tone = 'accent', onPress }: SbFilledButtonProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const isQuiet = tone === 'quiet';
  const foregroundColor = isQuiet ? colors.label : colors.onAccent;
  return (
    <SbPressable accessibilityLabel={accessibilityLabel ?? label} onPress={onPress}>
      <View style={[styles.button, isQuiet && styles.quietButton]}>
        <SbText variant="button" color={foregroundColor} numberOfLines={sbMaximumLines.singleLine}>
          {label}
        </SbText>
        {trailingIconName !== undefined && <MaterialIcons name={trailingIconName} size={ICON_SIZE} color={foregroundColor} />}
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    button: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: sbSpacing.small,
      minHeight: sbSize.minimumTouchTarget,
      paddingHorizontal: sbSpacing.large,
      paddingVertical: sbSpacing.compact,
      borderRadius: sbRadius.button,
      backgroundColor: colors.accent,
    },
    quietButton: { backgroundColor: colors.surfaceRaised },
  });
