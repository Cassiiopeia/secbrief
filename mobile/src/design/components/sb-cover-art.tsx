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

/**
 * 커버 그림 9종 (주제 바로가기·통계 타일용 자체 도형, 기사 사진 대신 쓰지 않음): target 과녁, lock 자물쇠, fingerprint 지문, terminal 터미널, cylinder 원통,
 * calendar 달력, shield 방패, network 연결망, signal 전파
 */
export type SbCoverKind = 'target' | 'lock' | 'fingerprint' | 'terminal' | 'cylinder' | 'calendar' | 'shield' | 'network' | 'signal';

export type { SbCoverPalette };

/** 회색 바탕 커버 색 (주제 바로가기) */
export function toSbNeutralCoverPalette(colors: SbColors): SbCoverPalette {
  return { back: colors.surfaceRaised, front: colors.label, mark: colors.accent };
}

interface SbCoverArtProps {
  kind: SbCoverKind;
  palette: SbCoverPalette;
}

/** 커버 그림: 부모 크기를 가득 채움 (비율이 다르면 넘치는 쪽을 자름) */
export function SbCoverArt({ kind, palette }: SbCoverArtProps) {
  return (
    <Svg viewBox={COVER_VIEW_BOX} preserveAspectRatio="xMidYMid slice" style={styles.fill}>
      {kind === 'target' && <TargetShape palette={palette} />}
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
