import { StyleSheet } from 'react-native';
import Svg from 'react-native-svg';

import type { SbColors } from '../tokens';
import {
  CalendarShape,
  COVER_VIEW_BOX,
  CylinderShape,
  FingerprintShape,
  LockShape,
  NetworkShape,
  type SbCoverPalette,
  ShieldShape,
  SignalShape,
  TargetShape,
  TerminalShape,
} from './sb-cover-shapes';
import { useSbPulse } from './use-sb-pulse';

/**
 * 커버 그림 9종 (사진 대신 쓰는 자체 도형): target 과녁, lock 자물쇠, fingerprint 지문, terminal 터미널, cylinder 원통,
 * calendar 달력, shield 방패, network 연결망, signal 전파
 */
export type SbCoverKind = 'target' | 'lock' | 'fingerprint' | 'terminal' | 'cylinder' | 'calendar' | 'shield' | 'network' | 'signal';

/** 커버 색 조합 4종: feature 보라 바탕, accent 민트 바탕, neutral 회색 바탕, ink 먹색 바탕 */
export type SbCoverTone = 'feature' | 'accent' | 'neutral' | 'ink';

export type { SbCoverPalette };

/** 색 조합 이름 > 실제 세 색 */
export function toSbCoverPalette(colors: SbColors, tone: SbCoverTone): SbCoverPalette {
  switch (tone) {
    case 'feature':
      return { back: colors.feature, front: colors.onFeature, mark: colors.accent };
    case 'accent':
      return { back: colors.accent, front: colors.onAccent, mark: colors.feature };
    case 'neutral':
      return { back: colors.surfaceRaised, front: colors.label, mark: colors.accent };
    case 'ink':
      return { back: colors.coverInk, front: colors.accent, mark: colors.onFeature };
  }
}

interface SbCoverArtProps {
  kind: SbCoverKind;
  palette: SbCoverPalette;
  /** 과녁 고리 맥박 여부 (과녁 그림에만 적용) */
  isPulsing?: boolean;
}

/** 커버 그림: 부모 크기를 가득 채움 (비율이 다르면 넘치는 쪽을 자름) */
export function SbCoverArt({ kind, palette, isPulsing = false }: SbCoverArtProps) {
  const pulse = useSbPulse(isPulsing && kind === 'target');
  return (
    <Svg viewBox={COVER_VIEW_BOX} preserveAspectRatio="xMidYMid slice" style={styles.fill}>
      {kind === 'target' && <TargetShape palette={palette} pulse={pulse} />}
      {kind === 'lock' && <LockShape palette={palette} />}
      {kind === 'fingerprint' && <FingerprintShape palette={palette} />}
      {kind === 'terminal' && <TerminalShape palette={palette} />}
      {kind === 'cylinder' && <CylinderShape palette={palette} />}
      {kind === 'calendar' && <CalendarShape palette={palette} />}
      {kind === 'shield' && <ShieldShape palette={palette} />}
      {kind === 'network' && <NetworkShape palette={palette} />}
      {kind === 'signal' && <SignalShape palette={palette} />}
    </Svg>
  );
}

const styles = StyleSheet.create({
  fill: { width: '100%', height: '100%' },
});
