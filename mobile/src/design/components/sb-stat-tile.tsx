import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbCapsLabel } from './sb-caps-label';
import { SbCountUpText } from './sb-count-up-text';
import { SbCoverArt, type SbCoverKind } from './sb-cover-art';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

/** 통계 타일 색조 2종: feature 보라 바탕(가장 강조할 통계 하나), neutral 회색 바탕 */
export type SbStatTileTone = 'feature' | 'neutral';

interface SbStatTileProps {
  label: string;
  /** 큰 값: 글자(예: 랜섬웨어) 또는 0 에서 세어 올라가는 숫자(예: 12 + 일) */
  value: { text: string } | { countTarget: number; countUnit: string };
  detail: string;
  tone: SbStatTileTone;
  /** 오른쪽 아래에 흐리게 깔리는 배경 그림 (없으면 생략) */
  backgroundCoverKind?: SbCoverKind;
  onPress?: () => void;
}

const MINIMUM_HEIGHT = 104;
// 배경 그림: 크기, 타일 밖으로 내보내는 거리, 흐림 정도 (글자를 가리지 않게 옅게)
const BACKGROUND_ART_SIZE = 96;
const BACKGROUND_ART_OVERFLOW = 22;
const BACKGROUND_ART_OPACITY = 0.22;

/** 통계 타일: 위 라벨 + 큰 값 + 한 줄 설명 */
export function SbStatTile({ label, value, detail, tone, backgroundCoverKind, onPress }: SbStatTileProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const isFeature = tone === 'feature';
  const backgroundColor = isFeature ? colors.feature : colors.surfaceRaised;
  const foregroundColor = isFeature ? colors.onFeature : colors.label;
  return (
    <SbPressable accessibilityLabel={label} onPress={onPress} isFilling>
      <View style={[styles.tile, isFeature ? styles.featureTile : styles.neutralTile]}>
        {backgroundCoverKind !== undefined && (
          <View style={styles.backgroundArt}>
            <SbCoverArt
              kind={backgroundCoverKind}
              palette={{ back: backgroundColor, front: foregroundColor, mark: colors.accent }}
            />
          </View>
        )}
        <SbCapsLabel text={label} color={foregroundColor} />
        <View style={styles.value}>
          {'text' in value ? (
            <SbText variant="statValue" color={foregroundColor}>
              {value.text}
            </SbText>
          ) : (
            <SbCountUpText
              targetValue={value.countTarget}
              unit={value.countUnit}
              variant="statValue"
              color={foregroundColor}
            />
          )}
        </View>
        <SbText variant="caption" color={foregroundColor}>
          {detail}
        </SbText>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    tile: {
      flex: 1,
      minHeight: MINIMUM_HEIGHT,
      padding: sbSpacing.statTilePadding,
      borderRadius: sbRadius.tile,
      overflow: 'hidden',
    },
    featureTile: { backgroundColor: colors.feature },
    neutralTile: { backgroundColor: colors.surfaceRaised },
    backgroundArt: {
      position: 'absolute',
      right: -BACKGROUND_ART_OVERFLOW,
      bottom: -BACKGROUND_ART_OVERFLOW,
      width: BACKGROUND_ART_SIZE,
      height: BACKGROUND_ART_SIZE,
      opacity: BACKGROUND_ART_OPACITY,
    },
    value: { marginTop: sbSpacing.large, marginBottom: sbSpacing.extraSmall },
  });
