import { useEffect } from 'react';
import { ScrollView, StyleSheet } from 'react-native';
import Animated, { interpolateColor, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbMotion, sbRadius, sbSize, sbSpacing } from '../tokens';
import { playSbHaptic } from './sb-haptics';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

export interface SbFilterChipOption<Value extends string> {
  value: Value;
  label: string;
}

interface SbFilterChipsProps<Value extends string> {
  options: SbFilterChipOption<Value>[];
  selectedValue: Value;
  onSelected: (value: Value) => void;
  /** accent 첫째 줄 칩(선택 = 강조색), quiet 둘째 줄 칩(선택 = 본문색 채움: 첫째 줄보다 덜 튀게) */
  tone?: 'accent' | 'quiet';
}

// 칩 높이 (보이는 크기). 터치 영역은 위아래 여유를 더해 최소 터치 높이까지 넓힘
const CHIP_HEIGHT = 36;
const CHIP_HIT_SLOP_VERTICAL = (sbSize.minimumTouchTarget - CHIP_HEIGHT) / 2;
const CHIP_HIT_SLOP = { top: CHIP_HIT_SLOP_VERTICAL, bottom: CHIP_HIT_SLOP_VERTICAL };

/** 필터 칩 한 줄: 선택 칩은 강조색 바탕 + 강조색 위 글자 (넘치면 옆으로 넘겨 봄) */
export function SbFilterChips<Value extends string>({ options, selectedValue, onSelected, tone = 'accent' }: SbFilterChipsProps<Value>) {
  const onChipPressed = (value: Value) => {
    if (value !== selectedValue) playSbHaptic('selection');
    onSelected(value);
  };
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.chips}
      accessibilityRole="tablist">
      {options.map((option) => (
        <SbFilterChip
          key={option.value}
          label={option.label}
          isSelected={option.value === selectedValue}
          isQuiet={tone === 'quiet'}
          onPress={() => onChipPressed(option.value)}
        />
      ))}
    </ScrollView>
  );
}

interface SbFilterChipProps {
  label: string;
  isSelected: boolean;
  isQuiet: boolean;
  onPress: () => void;
}

/** 칩 하나: 선택 여부가 바뀌면 바탕색이 부드럽게 바뀜 */
function SbFilterChip({ label, isSelected, isQuiet, onPress }: SbFilterChipProps) {
  const { colors } = useSbTheme();
  const restingColor = isQuiet ? colors.surface : colors.surfaceRaised;
  const selectedColor = isQuiet ? colors.label : colors.accent;
  const selectedLabelColor = isQuiet ? colors.background : colors.onAccent;
  // 선택 진행도 (0 안 고름 ~ 1 고름)
  const selectionProgress = useSharedValue(isSelected ? 1 : 0);
  useEffect(() => {
    selectionProgress.set(
      withTiming(isSelected ? 1 : 0, { duration: sbMotion.tabSwitchMilliseconds, easing: sbMotion.tabSwitchEasing }),
    );
  }, [isSelected, selectionProgress]);
  const animatedStyle = useAnimatedStyle(() => ({
    backgroundColor: interpolateColor(selectionProgress.value, [0, 1], [restingColor, selectedColor]),
  }));
  return (
    <SbPressable
      accessibilityLabel={label}
      accessibilityRole="tab"
      accessibilityState={{ selected: isSelected }}
      hitSlop={CHIP_HIT_SLOP}
      onPress={onPress}>
      <Animated.View style={[styles.chip, animatedStyle]}>
        <SbText variant="button" color={isSelected ? selectedLabelColor : colors.secondaryLabel}>
          {label}
        </SbText>
      </Animated.View>
    </SbPressable>
  );
}

const styles = StyleSheet.create({
  chips: {
    gap: sbSpacing.small,
    paddingHorizontal: sbSpacing.pageHorizontal,
    paddingTop: sbSpacing.extraSmall,
    paddingBottom: sbSpacing.small,
  },
  chip: {
    minHeight: CHIP_HEIGHT,
    justifyContent: 'center',
    paddingHorizontal: sbSpacing.medium,
    borderRadius: sbRadius.pill,
  },
});
