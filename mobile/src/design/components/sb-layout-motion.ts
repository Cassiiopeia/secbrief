import { Keyframe, ReduceMotion } from 'react-native-reanimated';

import { sbMotion } from '../tokens';

/**
 * 붙고 떨어지는 움직임 (Reanimated 레이아웃 애니메이션) 공통 정의: 펼침, 접힘
 * 투명도·이동만 (UI 스레드), 동작 줄이기가 켜져 있으면 바로 바뀜 (ReduceMotion.System)
 */

// 펼침: 살짝 위에서 내려오며 나타남
export const sbRevealEntering = new Keyframe({
  0: { opacity: 0, transform: [{ translateY: -sbMotion.revealOffset }] },
  100: { opacity: 1, transform: [{ translateY: 0 }], easing: sbMotion.revealEasing },
})
  .duration(sbMotion.revealMilliseconds)
  .reduceMotion(ReduceMotion.System);

// 접힘: 짧게 흐려지며 사라짐
export const sbConcealExiting = new Keyframe({
  0: { opacity: 1 },
  100: { opacity: 0, easing: sbMotion.concealEasing },
})
  .duration(sbMotion.concealMilliseconds)
  .reduceMotion(ReduceMotion.System);
