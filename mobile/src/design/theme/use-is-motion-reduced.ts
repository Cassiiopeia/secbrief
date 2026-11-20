import { useReducedMotion } from 'react-native-reanimated';

/**
 * 기기의 "동작 줄이기" 설정 여부 (앱 시작 시점 값). 켜져 있으면 등장·반복 애니메이션을 끈다
 */
export function useIsMotionReduced(): boolean {
  return useReducedMotion();
}
