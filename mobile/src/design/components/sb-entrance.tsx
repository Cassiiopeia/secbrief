import { type ReactNode, useEffect } from 'react';
import Animated, { useAnimatedStyle, useSharedValue, withDelay, withTiming } from 'react-native-reanimated';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { shouldStaggerListItem, sbMotion, toSbStaggerDelayMilliseconds } from '../tokens';

interface SbEntranceProps {
  /** 등장 순번 (0 부터, 순번 x 순차 지연만큼 늦게 나타남) */
  order: number;
  children: ReactNode;
}

/** 첫 진입 등장: 아래에서 살짝 올라오며 나타남 (UI 스레드, 투명도·이동만). 동작 줄이기가 켜져 있으면 바로 보임 */
export function SbEntrance({ order, children }: SbEntranceProps) {
  const isMotionReduced = useIsMotionReduced();
  const progress = useSharedValue(isMotionReduced ? 1 : 0);
  useEffect(() => {
    if (isMotionReduced) {
      progress.set(1);
      return;
    }
    progress.set(
      withDelay(
        toSbStaggerDelayMilliseconds(order, isMotionReduced),
        withTiming(1, { duration: sbMotion.entranceMilliseconds, easing: sbMotion.entranceEasing }),
      ),
    );
  }, [isMotionReduced, order, progress]);
  const animatedStyle = useAnimatedStyle(() => ({
    opacity: progress.value,
    transform: [{ translateY: (1 - progress.value) * sbMotion.entranceOffset }],
  }));
  return <Animated.View style={animatedStyle}>{children}</Animated.View>;
}

interface SbListEntranceProps {
  /** 목록 안 순번 (0 부터) */
  itemIndex: number;
  children: ReactNode;
}

/** 목록 항목 등장: 앞쪽 몇 건만 순차 등장, 그 뒤는 바로 보임 (스크롤로 뒤늦게 붙는 행이 늦게 뜨지 않게) */
export function SbListEntrance({ itemIndex, children }: SbListEntranceProps) {
  if (!shouldStaggerListItem(itemIndex)) return children;
  return <SbEntrance order={itemIndex}>{children}</SbEntrance>;
}
