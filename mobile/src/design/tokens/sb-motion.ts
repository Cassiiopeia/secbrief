import { Easing } from 'react-native-reanimated';

/**
 * 움직임 토큰: 지속시간·지연(단위: 밀리초)·곡선·크기. 애니메이션 값은 여기서만 정한다
 */
export const sbMotion = {
  /** 첫 진입 등장 (아래에서 올라오며 나타남) */
  entranceMilliseconds: 320,
  entranceEasing: Easing.out(Easing.cubic),
  /** 등장 순차 지연 (순번 x 이 값) */
  entranceStaggerMilliseconds: 60,
  /** 등장 시작 위치 (아래로 내려가 있는 거리, 논리 픽셀) */
  entranceOffset: 14,
  /** 목록 등장을 순차로 보여 주는 항목 수 (그 뒤 항목은 바로 보임: 스크롤 중 늦게 뜨지 않게) */
  entranceListItemCount: 8,
  /** 누름 반응 2종: 타일·버튼은 축소, 목록 행·탭은 흐려짐 */
  pressMilliseconds: 120,
  pressEasing: Easing.out(Easing.quad),
  pressScale: 0.97,
  pressOpacity: 0.55,
  /** 축소 누름을 뗄 때 제 크기로 돌아오는 스프링 (살짝 탄력) */
  pressReleaseSpring: { damping: 20, stiffness: 420, mass: 0.6 },
  /** 썸네일 이미지가 커버 그림 위로 나타나는 시간 */
  imageFadeMilliseconds: 220,
  /** 반복 맥박 (과녁 고리, 최신 항목 레일 점) */
  pulseMilliseconds: 1600,
  pulseEasing: Easing.inOut(Easing.quad),
  /** 통계 숫자 세어 올리기 */
  countUpMilliseconds: 700,
  /** 비율 막대가 0 에서 제 길이까지 자라는 시간 (동향 주제) */
  barGrowMilliseconds: 520,
  /** 탭 전환 (화면 교차 흐림, 칩 색 바뀜) */
  tabSwitchMilliseconds: 180,
  tabSwitchEasing: Easing.out(Easing.quad),
  /** 탭바 선택 막대 이동·선택 아이콘 커짐 (살짝 튕기며 멈추는 스프링) */
  tabIndicatorSpring: { damping: 20, stiffness: 260, mass: 0.9 },
  tabIconSelectedScale: 1.1,
  /** 로딩 뼈대 깜박임: 한 번 옅어지는 시간, 가장 옅을 때 불투명도 */
  skeletonMilliseconds: 1100,
  skeletonEasing: Easing.inOut(Easing.quad),
  skeletonDimmestOpacity: 0.45,
  /** 조건부 구역 펼침·접힘 (방해 금지 시각 행, 상태 안내): 나타남, 사라짐, 시작 위치 */
  revealMilliseconds: 260,
  revealEasing: Easing.out(Easing.cubic),
  concealMilliseconds: 160,
  concealEasing: Easing.in(Easing.quad),
  revealOffset: 8,
  /** 상태 그림 등장 (빈 상태·오류): 살짝 작게 시작해 부드럽게 제 크기로 (튕김 거의 없음) */
  illustrationSpring: { damping: 18, stiffness: 180, mass: 1 },
  illustrationStartScale: 0.88,
  /** 상태 화면 글이 그림 뒤에 따라 나오는 순번 */
  stateTextOrder: 1,
  /** 새 글 점: 톡 튀어나오고(스프링) 읽으면 줄어들며 사라짐 */
  dotPopSpring: { damping: 12, stiffness: 340, mass: 0.6 },
  dotHideMilliseconds: 180,
  /** 알림 막대(저장 확인 등): 위에서 내려오는 스프링, 보이는 시간, 사라지는 시간, 시작 위치 */
  toastSpring: { damping: 20, stiffness: 240, mass: 0.9 },
  toastVisibleMilliseconds: 2200,
  toastHideMilliseconds: 200,
  toastOffset: 28,
} as const;

/** 동작 줄이기 반영 지속시간 (켜져 있으면 0: 바로 바뀜) */
export function toSbMotionMilliseconds(durationMilliseconds: number, isMotionReduced: boolean): number {
  return isMotionReduced ? 0 : durationMilliseconds;
}

/** 순차 등장 지연 (순번 x 순차 지연, 동작 줄이기면 0) */
export function toSbStaggerDelayMilliseconds(order: number, isMotionReduced: boolean): number {
  return isMotionReduced ? 0 : Math.max(order, 0) * sbMotion.entranceStaggerMilliseconds;
}

/** 목록 항목 순차 등장 여부 (앞쪽 몇 건만: 긴 목록 스크롤 중 늦게 뜨지 않게) */
export function shouldStaggerListItem(itemIndex: number): boolean {
  return itemIndex >= 0 && itemIndex < sbMotion.entranceListItemCount;
}

/** 세어 올리기 진행도 곡선 (0~1 > 0~1, easeOutCubic) */
export function toCountUpProgress(linearProgress: number): number {
  return 1 - Math.pow(1 - linearProgress, 3);
}
