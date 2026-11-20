import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbFilledButtonProps {
  label: string;
  onPress: () => void;
}

/** 채움 버튼 (민트 바탕) */
export function SbFilledButton({ label, onPress }: SbFilledButtonProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable accessibilityLabel={label} onPress={onPress}>
      <View style={styles.button}>
        <SbText variant="button" color={colors.onAccent}>
          {label}
        </SbText>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    button: {
      backgroundColor: colors.accent,
      borderRadius: sbRadius.button,
      paddingHorizontal: sbSpacing.large,
      paddingVertical: sbSpacing.compact,
      alignItems: 'center',
    },
  });
