import { useEffect } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { useSbStyles } from '../theme/use-sb-theme';
import { type SbColors, sbMotion, sbRadius, sbSize } from '../tokens';

/**
 * 안 본 글 점 (알림 종, 알림함 칸·줄 공통): 보일 때 톡 튀어나오고(스프링) 숨길 때 줄어들며 사라짐
 * 자리는 늘 차지함 (숨겨도 글 위치가 흔들리지 않게). 읽기 도우미에는 붙는 쪽이 글로 알림
 */
export function SbUnreadDot({ isVisible = true }: { isVisible?: boolean }) {
  const styles = useSbStyles(createStyles);
  const isMotionReduced = useIsMotionReduced();
  // 크기 배율 (0 숨김 ~ 1 보임). 처음엔 0 에서 시작해 튀어나옴
  const scale = useSharedValue(isMotionReduced && isVisible ? 1 : 0);
  useEffect(() => {
    if (isMotionReduced) {
      scale.set(isVisible ? 1 : 0);
      return;
    }
    scale.set(
      isVisible
        ? withSpring(1, sbMotion.dotPopSpring)
        : withTiming(0, { duration: sbMotion.dotHideMilliseconds, easing: sbMotion.concealEasing }),
    );
  }, [isMotionReduced, isVisible, scale]);
  const animatedStyle = useAnimatedStyle(() => ({ opacity: Math.min(scale.value * 2, 1), transform: [{ scale: scale.value }] }));
  return <Animated.View style={[styles.dot, animatedStyle]} accessible={false} importantForAccessibility="no" />;
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    // 긴급 표시 색 (밝은 테마 보라, 어두운 테마 민트: 바탕과 대비되는 쪽)
    dot: { width: sbSize.unreadDot, height: sbSize.unreadDot, borderRadius: sbRadius.pill, backgroundColor: colors.urgent },
  });
