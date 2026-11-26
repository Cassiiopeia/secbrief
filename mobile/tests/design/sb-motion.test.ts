/**
 * 움직임 토큰·동작 줄이기 규칙: 지속시간 범위, 순차 등장 지연, 동작 줄이기면 0
 */
import { shouldStaggerListItem, sbMotion, toSbMotionMilliseconds, toSbStaggerDelayMilliseconds } from '../../src/design/tokens/sb-motion';

// 곡선 함수만 쓰는 토큰 파일이라 Reanimated 네이티브 초기화 없이 Easing 만 흉내 냄
jest.mock('react-native-reanimated', () => {
  const toCurve = () => (progress: number) => progress;
  return { Easing: { in: toCurve, out: toCurve, inOut: toCurve, cubic: toCurve(), quad: toCurve() } };
});

// 한 번 움직이는 전환의 허용 범위 (밀리초): 너무 짧으면 안 보이고, 길면 느리게 느껴짐
const MINIMUM_TRANSITION_MILLISECONDS = 100;
const MAXIMUM_TRANSITION_MILLISECONDS = 800;

describe('움직임 토큰', () => {
  test('한 번 움직이는 전환은 100~800ms 안이다', () => {
    const transitionMilliseconds = [
      sbMotion.entranceMilliseconds,
      sbMotion.pressMilliseconds,
      sbMotion.imageFadeMilliseconds,
      sbMotion.countUpMilliseconds,
      sbMotion.barGrowMilliseconds,
      sbMotion.tabSwitchMilliseconds,
      sbMotion.revealMilliseconds,
      sbMotion.concealMilliseconds,
      sbMotion.dotHideMilliseconds,
      sbMotion.toastHideMilliseconds,
    ];
    for (const milliseconds of transitionMilliseconds) {
      expect(milliseconds).toBeGreaterThanOrEqual(MINIMUM_TRANSITION_MILLISECONDS);
      expect(milliseconds).toBeLessThanOrEqual(MAXIMUM_TRANSITION_MILLISECONDS);
    }
  });

  test('사라짐은 나타남보다 빠르다 (떠나는 것은 짧게)', () => {
    expect(sbMotion.concealMilliseconds).toBeLessThan(sbMotion.revealMilliseconds);
    expect(sbMotion.toastHideMilliseconds).toBeLessThan(sbMotion.toastVisibleMilliseconds);
  });

  test('스프링은 멈추는 값이다 (감쇠·강성·질량 모두 양수)', () => {
    for (const spring of [sbMotion.pressReleaseSpring, sbMotion.tabIndicatorSpring, sbMotion.illustrationSpring, sbMotion.dotPopSpring, sbMotion.toastSpring]) {
      expect(spring.damping).toBeGreaterThan(0);
      expect(spring.stiffness).toBeGreaterThan(0);
      expect(spring.mass).toBeGreaterThan(0);
    }
  });

  test('누름·그림 시작 크기는 1 보다 살짝 작다 (0.8 이상)', () => {
    for (const scale of [sbMotion.pressScale, sbMotion.illustrationStartScale]) {
      expect(scale).toBeGreaterThanOrEqual(0.8);
      expect(scale).toBeLessThan(1);
    }
  });
});

describe('동작 줄이기', () => {
  test('켜져 있으면 지속시간 0, 꺼져 있으면 그대로', () => {
    expect(toSbMotionMilliseconds(sbMotion.revealMilliseconds, true)).toBe(0);
    expect(toSbMotionMilliseconds(sbMotion.revealMilliseconds, false)).toBe(sbMotion.revealMilliseconds);
  });

  test('켜져 있으면 순차 지연 0', () => {
    expect(toSbStaggerDelayMilliseconds(5, true)).toBe(0);
  });
});

describe('순차 등장', () => {
  test('순번 x 순차 지연, 음수 순번은 0', () => {
    expect(toSbStaggerDelayMilliseconds(0, false)).toBe(0);
    expect(toSbStaggerDelayMilliseconds(3, false)).toBe(3 * sbMotion.entranceStaggerMilliseconds);
    expect(toSbStaggerDelayMilliseconds(-1, false)).toBe(0);
  });

  test('목록은 앞쪽 몇 건만 순차 등장 (긴 목록 스크롤 중 늦게 뜨지 않게)', () => {
    expect(shouldStaggerListItem(0)).toBe(true);
    expect(shouldStaggerListItem(sbMotion.entranceListItemCount - 1)).toBe(true);
    expect(shouldStaggerListItem(sbMotion.entranceListItemCount)).toBe(false);
    expect(shouldStaggerListItem(-1)).toBe(false);
  });
});
