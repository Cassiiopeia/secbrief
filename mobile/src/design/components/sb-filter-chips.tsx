import { Pressable, StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbText } from './sb-text';

export interface SbFilterChipOption<Value extends string> {
  value: Value;
  label: string;
}

interface SbFilterChipsProps<Value extends string> {
  options: SbFilterChipOption<Value>[];
  selectedValue: Value;
  onSelected: (value: Value) => void;
}

/** 필터 칩 한 줄: 선택 칩은 강조색 바탕 + 강조색 위 글자 */
export function SbFilterChips<Value extends string>({ options, selectedValue, onSelected }: SbFilterChipsProps<Value>) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.chips}>
      {options.map((option) => {
        const isSelected = option.value === selectedValue;
        return (
          <Pressable
            key={option.value}
            accessibilityRole="button"
            accessibilityLabel={option.label}
            accessibilityState={{ selected: isSelected }}
            onPress={() => onSelected(option.value)}
            style={[styles.chip, isSelected && styles.selectedChip]}>
            <SbText variant="button" color={isSelected ? colors.onAccent : colors.secondaryLabel}>
              {option.label}
            </SbText>
          </Pressable>
        );
      })}
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    chips: {
      flexDirection: 'row',
      gap: sbSpacing.small,
      paddingHorizontal: sbSpacing.pageHorizontal,
      paddingVertical: sbSpacing.small,
    },
    chip: {
      paddingHorizontal: sbSpacing.medium,
      paddingVertical: sbSpacing.small,
      borderRadius: sbRadius.pill,
      backgroundColor: colors.surfaceRaised,
    },
    selectedChip: { backgroundColor: colors.accent },
  });
