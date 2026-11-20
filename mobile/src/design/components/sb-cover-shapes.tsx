import type { ReactNode } from 'react';
import Animated, { type SharedValue, useAnimatedProps } from 'react-native-reanimated';
import { Circle, Ellipse, G, Path, Rect } from 'react-native-svg';

/** 커버 그림의 세 색 (바탕, 도형, 포인트) */
export interface SbCoverPalette {
  back: string;
  front: string;
  mark: string;
}

// 그림 좌표계 (시안 SVG 와 같은 160 x 100)
export const COVER_VIEW_BOX = '0 0 160 100';
const VIEW_WIDTH = 160;
const VIEW_HEIGHT = 100;

const AnimatedCircle = Animated.createAnimatedComponent(Circle);

const Backdrop = ({ color }: { color: string }) => <Rect width={VIEW_WIDTH} height={VIEW_HEIGHT} fill={color} />;

// 과녁: 점 격자 12 x 5, 맥박 고리의 쉬는 반지름과 커지는 폭·옅어지는 정도
const DOT_COLUMN_COUNT = 12;
const DOT_COUNT = 60;
const PULSE_RING_RADIUS = 38;
const PULSE_RING_GROWTH = 7;
const PULSE_RING_OPACITY = 0.4;

/** 과녁 (취약점·침투): 점 격자를 가르는 사선과 과녁. 바깥 고리가 은은하게 맥박침 */
export function TargetShape({ palette, pulse }: { palette: SbCoverPalette; pulse: SharedValue<number> }) {
  const pulseRingProps = useAnimatedProps(() => ({
    r: PULSE_RING_RADIUS + PULSE_RING_GROWTH * pulse.value,
    opacity: PULSE_RING_OPACITY * (1 - pulse.value * 0.7),
  }));
  return (
    <G>
      <Backdrop color={palette.back} />
      {Array.from({ length: DOT_COUNT }, (_, dotIndex) => (
        <Circle
          key={dotIndex}
          cx={10 + (dotIndex % DOT_COLUMN_COUNT) * 13}
          cy={12 + Math.floor(dotIndex / DOT_COLUMN_COUNT) * 19}
          r={2.2}
          fill={palette.front}
        />
      ))}
      <AnimatedCircle cx={112} cy={52} fill="none" stroke={palette.front} strokeWidth={1.5} animatedProps={pulseRingProps} />
      <Circle cx={112} cy={52} r={34} fill="none" stroke={palette.front} strokeWidth={3} />
      <Circle cx={112} cy={52} r={18} fill="none" stroke={palette.front} strokeWidth={3} />
      <Circle cx={112} cy={52} r={6} fill={palette.mark} />
      <Path d="M-10 96 L92 30" fill="none" stroke={palette.mark} strokeWidth={6} />
    </G>
  );
}

const STRIPE_COUNT = 9;

/** 자물쇠 (랜섬웨어·암호화): 기운 줄무늬 위 자물쇠 */
export function LockShape({ palette }: { palette: SbCoverPalette }): ReactNode {
  return (
    <G>
      <Backdrop color={palette.back} />
      {Array.from({ length: STRIPE_COUNT }, (_, stripeIndex) => (
        <Rect
          key={stripeIndex}
          x={stripeIndex * 20 - 6}
          y={0}
          width={8}
          height={VIEW_HEIGHT}
          fill={palette.front}
          opacity={0.18}
          transform="skewX(-20)"
        />
      ))}
      <Path d="M62 50 v-14 a18 18 0 0 1 36 0 v14" fill="none" stroke={palette.front} strokeWidth={7} />
      <Rect x={50} y={48} width={60} height={42} rx={8} fill={palette.front} />
      <Circle cx={80} cy={66} r={6} fill={palette.mark} />
      <Rect x={77.5} y={68} width={5} height={12} fill={palette.mark} />
    </G>
  );
}

// 지문 호의 반지름 (안쪽부터)
const FINGERPRINT_RADII = [8, 18, 28, 38, 48, 58];

/** 지문 (개인정보): 겹친 호와 밑줄 */
export function FingerprintShape({ palette }: { palette: SbCoverPalette }): ReactNode {
  return (
    <G>
      <Backdrop color={palette.back} />
      {FINGERPRINT_RADII.map((radius) => (
        <Path
          key={radius}
          d={`M${80 - radius} 78 a${radius} ${radius * 1.15} 0 0 1 ${radius * 2} 0`}
          fill="none"
          stroke={palette.front}
          strokeWidth={4}
        />
      ))}
      <Rect x={20} y={84} width={120} height={5} fill={palette.mark} />
    </G>
  );
}

/** 터미널 (원격 접속·명령 실행): 프롬프트 꺾쇠와 커서 */
export function TerminalShape({ palette }: { palette: SbCoverPalette }): ReactNode {
  return (
    <G>
      <Backdrop color={palette.back} />
      <Path d="M26 30 L56 50 L26 70" fill="none" stroke={palette.front} strokeWidth={9} />
      <Rect x={66} y={62} width={42} height={9} fill={palette.mark} />
      {[0, 1, 2].map((rowIndex) => (
        <Rect
          key={rowIndex}
          x={116}
          y={24 + rowIndex * 16}
          width={30 - rowIndex * 8}
          height={6}
          fill={palette.front}
          opacity={0.5}
        />
      ))}
    </G>
  );
}

/** 원통 (데이터베이스): 쌓인 타원 3장, 가운데만 포인트 색 */
export function CylinderShape({ palette }: { palette: SbCoverPalette }): ReactNode {
  return (
    <G>
      <Backdrop color={palette.back} />
      {[0, 1, 2].map((layerIndex) => (
        <Ellipse
          key={layerIndex}
          cx={80}
          cy={28 + layerIndex * 22}
          rx={44}
          ry={13}
          fill={layerIndex === 1 ? palette.mark : palette.front}
        />
      ))}
    </G>
  );
}
