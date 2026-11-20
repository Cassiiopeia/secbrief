import { Easing } from 'react-native-reanimated';

/**
 * 움직임 토큰: 지속시간·지연(단위: 밀리초)·곡선·크기. 애니메이션 값은 여기서만 정한다
 * 곡선은 Flutter 판의 easeOutCubic / easeOut / easeInOut 에 대응
 */
export const sbMotion = {
  /** 첫 진입 등장 (아래에서 올라오며 나타남) */
  entranceMilliseconds: 320,
  entranceEasing: Easing.out(Easing.cubic),
  /** 등장 순차 지연 (순번 x 이 값) */
  entranceStaggerMilliseconds: 60,
  /** 등장 시작 위치 (아래로 내려가 있는 거리, 논리 픽셀) */
  entranceOffset: 14,
  /** 누름 축소 */
  pressMilliseconds: 120,
  pressEasing: Easing.out(Easing.quad),
  pressScale: 0.97,
  /** 반복 맥박 (과녁 고리, 최신 항목 레일 점) */
  pulseMilliseconds: 1600,
  pulseEasing: Easing.inOut(Easing.quad),
  /** 통계 숫자 세어 올리기 */
  countUpMilliseconds: 700,
  /** 탭 전환 (선택 막대) */
  tabSwitchMilliseconds: 180,
  tabSwitchEasing: Easing.out(Easing.quad),
  /** 로딩 뼈대 깜박임 */
  skeletonMilliseconds: 1100,
} as const;

/** 세어 올리기 진행도 곡선 (0~1 > 0~1, easeOutCubic) */
export function toCountUpProgress(linearProgress: number): number {
  return 1 - Math.pow(1 - linearProgress, 3);
}
