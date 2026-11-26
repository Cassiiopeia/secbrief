import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

/** 건수 타일 색조 3종: feature 보라 바탕(강조, 예: 악용 중 1건 이상), neutral 흰 바탕, muted 흐림(0건 "없음") */
export type SbCountTileTone = 'feature' | 'neutral' | 'muted';

interface SbCountTileProps {
  /** 큰 값 (예: +11, 없음) */
  valueLabel: string;
  title: string;
  /** 대표 이름 한 줄 (없으면 생략) */
  detail?: string;
  tone: SbCountTileTone;
  accessibilityLabel: string;
  onPress?: () => void;
}

/** 건수 타일 (#93 오늘 새 소식 띠): 큰 값 > 이름 > (대표 이름 한 줄). 가로 띠 안에 같은 너비로 놓임 */
export function SbCountTile({ valueLabel, title, detail, tone, accessibilityLabel, onPress }: SbCountTileProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const foregroundColor = tone === 'feature' ? colors.onFeature : tone === 'muted' ? colors.tertiaryLabel : colors.label;
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress}>
      <View style={[styles.tile, styles[tone]]}>
        <SbText variant="statValue" color={foregroundColor} numberOfLines={sbMaximumLines.singleLine}>
          {valueLabel}
        </SbText>
        <SbText variant="rowTitle" color={foregroundColor} numberOfLines={sbMaximumLines.singleLine}>
          {title}
        </SbText>
        {detail !== undefined && (
          <SbText variant="caption" color={tone === 'feature' ? colors.onFeature : colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
            {detail}
          </SbText>
        )}
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    tile: {
      width: sbSize.countTileWidth,
      minHeight: sbSize.countTileMinimumHeight,
      padding: sbSpacing.statTilePadding,
      gap: sbSpacing.hairline,
      borderRadius: sbRadius.tile,
    },
    feature: { backgroundColor: colors.feature },
    neutral: { backgroundColor: colors.surface },
    muted: { backgroundColor: colors.surface },
  });
