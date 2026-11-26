import type { ReactNode } from 'react';
import Animated from 'react-native-reanimated';

import { sbConcealExiting, sbRevealEntering } from './sb-layout-motion';

/**
 * 조건부 구역 감싸기: 붙을 때 살짝 내려오며 나타나고, 떨어질 때 흐려지며 사라짐
 * 예: 방해 금지 시각 행, 저장 실패·권한 안내. 화면은 entering/exiting 을 직접 쓰지 않고 이것을 씀
 */
export function SbReveal({ children }: { children: ReactNode }) {
  return (
    <Animated.View entering={sbRevealEntering} exiting={sbConcealExiting}>
      {children}
    </Animated.View>
  );
}
