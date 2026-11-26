import { Image } from 'expo-image';
import { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { sbMotion, sbSize } from '../tokens';
import { SB_STATE_ILLUSTRATION_SOURCES, type SbStateIllustrationKind } from './sb-state-illustration-sources';

/** 상태 그림: 살짝 작게 시작해 스프링으로 제 크기가 되며 나타남 (꾸밈이라 읽기 도우미는 건너뜀). 동작 줄이기면 바로 보임 */
export function SbStateIllustration({ kind }: { kind: SbStateIllustrationKind }) {
  const isMotionReduced = useIsMotionReduced();
  // 등장 진행도 (0 시작 ~ 1 제자리)
  const progress = useSharedValue(isMotionReduced ? 1 : 0);
  useEffect(() => {
    progress.set(isMotionReduced ? 1 : withSpring(1, sbMotion.illustrationSpring));
  }, [isMotionReduced, progress]);
  const animatedStyle = useAnimatedStyle(() => ({
    opacity: Math.min(progress.value, 1),
    transform: [{ scale: sbMotion.illustrationStartScale + (1 - sbMotion.illustrationStartScale) * progress.value }],
  }));
  return (
    <Animated.View style={[styles.frame, animatedStyle]} accessible={false} importantForAccessibility="no-hide-descendants">
      <Image source={SB_STATE_ILLUSTRATION_SOURCES[kind]} style={styles.frame} contentFit="contain" />
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  frame: { width: sbSize.stateIllustration, height: sbSize.stateIllustration },
});
