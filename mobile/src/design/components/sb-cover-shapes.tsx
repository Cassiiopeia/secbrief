import type { ReactNode } from 'react';
import { Circle, Ellipse, G, Line, Path, Rect } from 'react-native-svg';

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

const Backdrop = ({ color }: { color: string }) => <Rect width={VIEW_WIDTH} height={VIEW_HEIGHT} fill={color} />;

// 과녁: 점 격자 12 x 5, 바깥 옅은 고리 반지름·불투명도
const DOT_COLUMN_COUNT = 12;
const DOT_COUNT = 60;
const OUTER_RING_RADIUS = 38;
const OUTER_RING_OPACITY = 0.4;

/** 과녁 (취약점·침투): 점 격자를 가르는 사선과 과녁 */
export function TargetShape({ palette }: { palette: SbCoverPalette }): ReactNode {
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
      <Circle cx={112} cy={52} r={OUTER_RING_RADIUS} opacity={OUTER_RING_OPACITY} fill="none" stroke={palette.front} strokeWidth={1.5} />
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

// 달력 날짜 칸: 4 x 2, 그중 포인트 색으로 칠할 칸 번호 (행사 날)
const CALENDAR_COLUMN_COUNT = 4;
const CALENDAR_CELL_COUNT = 8;
const CALENDAR_MARKED_CELL_INDEX = 5;

/** 달력 (행사): 고리 달린 달력 판과 날짜 칸, 하루만 포인트 색 */
export function CalendarShape({ palette }: { palette: SbCoverPalette }): ReactNode {
  return (
    <G>
      <Backdrop color={palette.back} />
      <Rect x={38} y={22} width={84} height={64} rx={8} fill={palette.front} />
      <Rect x={38} y={22} width={84} height={16} rx={8} fill={palette.mark} />
      <Rect x={56} y={14} width={6} height={16} rx={3} fill={palette.front} />
      <Rect x={98} y={14} width={6} height={16} rx={3} fill={palette.front} />
      {Array.from({ length: CALENDAR_CELL_COUNT }, (_, cellIndex) => (
        <Rect
          key={cellIndex}
          x={48 + (cellIndex % CALENDAR_COLUMN_COUNT) * 17}
          y={46 + Math.floor(cellIndex / CALENDAR_COLUMN_COUNT) * 17}
          width={12}
          height={12}
          rx={3}
          fill={cellIndex === CALENDAR_MARKED_CELL_INDEX ? palette.mark : palette.back}
        />
      ))}
    </G>
  );
}

// 방패 옆 줄: 왼쪽·오른쪽 3줄씩 (세로 위치, 길이)
const SHIELD_SIDE_BARS = [
  { y: 32, width: 24 },
  { y: 48, width: 16 },
  { y: 64, width: 20 },
];

/** 방패 (보안 공지·정책): 가운데 방패와 확인 표시, 양옆 짧은 줄 */
export function ShieldShape({ palette }: { palette: SbCoverPalette }): ReactNode {
  return (
    <G>
      <Backdrop color={palette.back} />
      {SHIELD_SIDE_BARS.map((bar) => (
        <G key={bar.y} opacity={0.4}>
          <Rect x={12} y={bar.y} width={bar.width} height={6} fill={palette.front} />
          <Rect x={VIEW_WIDTH - 12 - bar.width} y={bar.y} width={bar.width} height={6} fill={palette.front} />
        </G>
      ))}
      <Path d="M80 12 L112 24 V50 C112 68 98 82 80 90 C62 82 48 68 48 50 V24 Z" fill={palette.front} />
      <Path d="M65 50 L76 61 L96 39" fill="none" stroke={palette.mark} strokeWidth={7} />
    </G>
  );
}

// 연결망: 가운데 마디와 둘레 마디 5개 (가운데에서 선으로 이음)
const NETWORK_CENTER = { x: 80, y: 50 };
const NETWORK_OUTER_NODES = [
  { x: 28, y: 26 },
  { x: 132, y: 22 },
  { x: 40, y: 80 },
  { x: 124, y: 78 },
  { x: 84, y: 8 },
];

/** 연결망 (공급망·일반): 선으로 이어진 마디, 가운데만 포인트 색 */
export function NetworkShape({ palette }: { palette: SbCoverPalette }): ReactNode {
  return (
    <G>
      <Backdrop color={palette.back} />
      {NETWORK_OUTER_NODES.map((node) => (
        <Line
          key={`line-${node.x}-${node.y}`}
          x1={NETWORK_CENTER.x}
          y1={NETWORK_CENTER.y}
          x2={node.x}
          y2={node.y}
          stroke={palette.front}
          strokeWidth={3}
        />
      ))}
      {NETWORK_OUTER_NODES.map((node) => (
        <Circle key={`node-${node.x}-${node.y}`} cx={node.x} cy={node.y} r={9} fill={palette.front} />
      ))}
      <Circle cx={NETWORK_CENTER.x} cy={NETWORK_CENTER.y} r={14} fill={palette.mark} />
    </G>
  );
}

// 전파: 퍼지는 호의 반지름 (안쪽부터), 호가 시작하는 점
const SIGNAL_RADII = [24, 44, 64, 84];
const SIGNAL_ORIGIN = { x: 36, y: 80 };

/** 전파 (일반 소식): 왼쪽 아래 점에서 퍼지는 호 */
export function SignalShape({ palette }: { palette: SbCoverPalette }): ReactNode {
  return (
    <G>
      <Backdrop color={palette.back} />
      {SIGNAL_RADII.map((radius) => (
        <Path
          key={radius}
          d={`M${SIGNAL_ORIGIN.x} ${SIGNAL_ORIGIN.y - radius} a${radius} ${radius} 0 0 1 ${radius} ${radius}`}
          fill="none"
          stroke={palette.front}
          strokeWidth={6}
        />
      ))}
      <Circle cx={SIGNAL_ORIGIN.x} cy={SIGNAL_ORIGIN.y} r={9} fill={palette.mark} />
    </G>
  );
}
