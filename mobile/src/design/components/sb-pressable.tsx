import type { ReactNode } from 'react';
import { Pressable, StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

import { sbMotion } from '../tokens';

interface SbPressableProps {
  children: ReactNode;
  accessibilityLabel: string;
  onPress?: () => void;
  /** 부모가 준 자리를 가득 채울지 (나란히 놓인 타일의 높이를 맞출 때) */
  isFilling?: boolean;
}

/** 누름 공통 래퍼: 누르는 동안 살짝 줄어듦. 동작 줄이기가 켜져 있으면 바로 바뀜(Reanimated 기본) */
export function SbPressable({ children, accessibilityLabel, onPress, isFilling = false }: SbPressableProps) {
  const scale = useSharedValue(1);
  const animatedStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));
  const animateTo = (targetScale: number) => {
    scale.set(withTiming(targetScale, { duration: sbMotion.pressMilliseconds, easing: sbMotion.pressEasing }));
  };
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      onPress={onPress}
      onPressIn={() => animateTo(sbMotion.pressScale)}
      onPressOut={() => animateTo(1)}
      style={isFilling && styles.fill}>
      <Animated.View style={[isFilling && styles.fill, animatedStyle]}>{children}</Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
});
