import { useEffect } from 'react';
import { type SharedValue, cancelAnimation, useSharedValue, withRepeat, withTiming } from 'react-native-reanimated';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { sbMotion } from '../tokens';

/**
 * 반복 맥박 값: 0 > 1 > 0 을 계속 오감. isActive 가 false 이거나 동작 줄이기가 켜져 있으면 0 으로 멈춤
 */
export function useSbPulse(isActive: boolean): SharedValue<number> {
  const pulse = useSharedValue(0);
  const isMotionReduced = useIsMotionReduced();
  useEffect(() => {
    if (!isActive || isMotionReduced) {
      cancelAnimation(pulse);
      pulse.set(0);
      return;
    }
    pulse.set(
      withRepeat(
        withTiming(1, { duration: sbMotion.pulseMilliseconds, easing: sbMotion.pulseEasing }),
        -1, // 끝없이 반복
        true, // 되돌아오며 반복
      ),
    );
    return () => cancelAnimation(pulse);
  }, [isActive, isMotionReduced, pulse]);
  return pulse;
}
