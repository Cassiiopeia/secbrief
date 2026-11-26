import { useState } from 'react';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';
import Animated from 'react-native-reanimated';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing } from '../tokens';
import { playSbHaptic } from './sb-haptics';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';
import { SbUnreadDot } from './sb-unread-dot';
import { useSbSlidingOffsetStyle } from './use-sb-sliding-offset';

export interface SbSegmentOption<Value extends string> {
  value: Value;
  label: string;
  /** 이름 옆 안 본 글 점 (읽기 도우미에는 '새 글 있음'을 덧붙임) */
  hasDot?: boolean;
}

interface SbSegmentedControlProps<Value extends string> {
  options: SbSegmentOption<Value>[];
  selectedValue: Value;
  onSelected: (value: Value) => void;
}

const TRACK_PADDING = 3;
const SEGMENT_DOT_ACCESSIBILITY_TEXT = '새 글 있음';

/** 세그먼트: 보기 방식을 하나 고름 (2~3개). 선택 알약이 고른 칸으로 미끄러져 감 */
export function SbSegmentedControl<Value extends string>({ options, selectedValue, onSelected }: SbSegmentedControlProps<Value>) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const [trackWidth, setTrackWidth] = useState(0);
  const segmentWidth = (trackWidth - TRACK_PADDING * 2) / options.length;
  const selectedIndex = Math.max(options.findIndex((option) => option.value === selectedValue), 0);
  const thumbOffsetStyle = useSbSlidingOffsetStyle(selectedIndex * segmentWidth, trackWidth > 0);
  // 알약 너비는 잰 값이라 StyleSheet 에 둘 수 없음
  const thumbWidthStyle = { width: segmentWidth };

  const onTrackLayout = (event: LayoutChangeEvent) => setTrackWidth(event.nativeEvent.layout.width);
  const onSegmentPressed = (value: Value) => {
    if (value !== selectedValue) playSbHaptic('selection');
    onSelected(value);
  };

  return (
    <View style={styles.wrap}>
      <View style={styles.track} onLayout={onTrackLayout} accessibilityRole="tablist">
        {trackWidth > 0 && <Animated.View style={[styles.thumb, thumbWidthStyle, thumbOffsetStyle]} />}
        {options.map((option) => {
          const isSelected = option.value === selectedValue;
          return (
            <SbPressable
              key={option.value}
              accessibilityLabel={option.hasDot ? `${option.label}, ${SEGMENT_DOT_ACCESSIBILITY_TEXT}` : option.label}
              accessibilityRole="tab"
              accessibilityState={{ selected: isSelected }}
              onPress={() => onSegmentPressed(option.value)}
              feedback="dim"
              isFilling>
              <View style={styles.segment}>
                <SbText variant="button" color={isSelected ? colors.background : colors.secondaryLabel} numberOfLines={sbMaximumLines.singleLine}>
                  {option.label}
                </SbText>
                {option.hasDot && <SbUnreadDot />}
              </View>
            </SbPressable>
          );
        })}
      </View>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { paddingHorizontal: sbSpacing.pageHorizontal, paddingVertical: sbSpacing.extraSmall },
    track: { flexDirection: 'row', padding: TRACK_PADDING, borderRadius: sbRadius.pill, backgroundColor: colors.surfaceRaised },
    // 선택 알약: 본문색 채움 (그 위 글자는 바탕색)
    thumb: {
      position: 'absolute',
      top: TRACK_PADDING,
      bottom: TRACK_PADDING,
      left: TRACK_PADDING,
      borderRadius: sbRadius.pill,
      backgroundColor: colors.label,
    },
    segment: {
      flexDirection: 'row',
      gap: sbSpacing.extraSmall,
      minHeight: sbSize.minimumTouchTarget - TRACK_PADDING * 2,
      alignItems: 'center',
      justifyContent: 'center',
      paddingHorizontal: sbSpacing.small,
    },
  });
