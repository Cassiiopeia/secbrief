import { StyleSheet } from 'react-native';
import Svg from 'react-native-svg';

import type { SbColors } from '../tokens';
import {
  COVER_VIEW_BOX,
  CylinderShape,
  FingerprintShape,
  LockShape,
  type SbCoverPalette,
  TargetShape,
  TerminalShape,
} from './sb-cover-shapes';
import { useSbPulse } from './use-sb-pulse';

/**
 * 주제별 커버 그림 5종 (사진 대신 쓰는 자체 도형 그림)
 * target 과녁(취약점), lock 자물쇠(랜섬웨어), fingerprint 지문(개인정보),
 * terminal 터미널(원격 접속), cylinder 원통(데이터베이스)
 */
export type SbCoverKind = 'target' | 'lock' | 'fingerprint' | 'terminal' | 'cylinder';

/** 커버 색 조합 3종: feature 보라 바탕, accent 민트 바탕, neutral 회색 바탕 */
export type SbCoverTone = 'feature' | 'accent' | 'neutral';

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
    </Svg>
  );
}

const styles = StyleSheet.create({
  fill: { width: '100%', height: '100%' },
});
