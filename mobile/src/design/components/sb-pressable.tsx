import type { ReactNode } from 'react';
import { type AccessibilityRole, type AccessibilityState, type Insets, Pressable, StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';

import { sbMotion } from '../tokens';

/** 누름 반응 2종: scale 살짝 줄어듦(타일·버튼), dim 흐려짐(목록 행·탭) */
export type SbPressFeedback = 'scale' | 'dim';

interface SbPressableProps {
  children: ReactNode;
  accessibilityLabel: string;
  /** 없으면 누름 없음 (반응·버튼 역할 모두 끔) */
  onPress?: () => void;
  feedback?: SbPressFeedback;
  /** 기본 button (탭은 tab) */
  accessibilityRole?: AccessibilityRole;
  accessibilityState?: AccessibilityState;
  /** 보이는 크기가 최소 터치 영역보다 작을 때 넓히는 여유 */
  hitSlop?: Insets;
  /** 부모가 준 자리를 가득 채울지 (나란히 놓인 타일의 높이를 맞출 때) */
  isFilling?: boolean;
}

/** 누름 공통 래퍼: 누르는 동안 줄어들거나 흐려짐. 동작 줄이기가 켜져 있으면 바로 바뀜(Reanimated 기본) */
export function SbPressable({
  children,
  accessibilityLabel,
  onPress,
  feedback = 'scale',
  accessibilityRole = 'button',
  accessibilityState,
  hitSlop,
  isFilling = false,
}: SbPressableProps) {
  const isPressable = onPress !== undefined;
  // 누름 진행도 (0 뗌 ~ 1 누름)
  const pressProgress = useSharedValue(0);
  const animatedStyle = useAnimatedStyle(() =>
    feedback === 'scale'
      ? { transform: [{ scale: 1 - (1 - sbMotion.pressScale) * pressProgress.value }] }
      : { opacity: 1 - (1 - sbMotion.pressOpacity) * pressProgress.value },
  );
  // 누를 때는 짧은 곡선, 축소 누름을 뗄 때는 스프링 (흐려짐은 넘침 없이 곡선으로)
  const animateTo = (targetProgress: number) => {
    const isScaleRelease = feedback === 'scale' && targetProgress === 0;
    pressProgress.set(
      isScaleRelease
        ? withSpring(targetProgress, sbMotion.pressReleaseSpring)
        : withTiming(targetProgress, { duration: sbMotion.pressMilliseconds, easing: sbMotion.pressEasing }),
    );
  };
  return (
    <Pressable
      accessibilityRole={isPressable ? accessibilityRole : 'text'}
      accessibilityLabel={accessibilityLabel}
      accessibilityState={accessibilityState}
      disabled={!isPressable}
      hitSlop={hitSlop}
      onPress={onPress}
      onPressIn={() => animateTo(1)}
      onPressOut={() => animateTo(0)}
      style={isFilling && styles.fill}>
      <Animated.View style={[isFilling && styles.fillContent, animatedStyle]}>{children}</Animated.View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  // 안쪽은 flexGrow 만: flex 1(기준 0)이면 높이 없는 부모 안에서 네이티브가 0 높이로 접음 (Android 탭바 사라짐)
  fillContent: { flexGrow: 1 },
});
