import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

export interface SbHashtag {
  /** # 없는 키워드 */
  keyword: string;
  /** 키워드 옆 작은 글자 (예: 12건) */
  countLabel?: string;
}

interface SbHashtagChipsProps {
  hashtags: SbHashtag[];
  /** large 는 검색 화면의 칩 구름 (크게, 건수 포함) */
  size?: 'small' | 'large';
  /** 읽기 도우미 문구 (키워드 > 문구) */
  toAccessibilityLabel: (hashtag: SbHashtag) => string;
  onHashtagPress: (keyword: string) => void;
}

const HASH_SIGN = '#';
const SMALL_CHIP_HEIGHT = 26;
const LARGE_CHIP_HEIGHT = 36;
// 작은 칩의 터치 영역을 최소 터치 높이까지 넓힘
const SMALL_CHIP_HIT_SLOP_VERTICAL = (sbSize.minimumTouchTarget - SMALL_CHIP_HEIGHT) / 2;
const SMALL_CHIP_HIT_SLOP = { top: SMALL_CHIP_HIT_SLOP_VERTICAL, bottom: SMALL_CHIP_HIT_SLOP_VERTICAL };

/** 해시태그 칩 묶음: #키워드 (+건수). 누르면 그 키워드 소식 목록. 칩이 없으면 아무것도 그리지 않음 */
export function SbHashtagChips({ hashtags, size = 'small', toAccessibilityLabel, onHashtagPress }: SbHashtagChipsProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  if (hashtags.length === 0) return null;
  const isLarge = size === 'large';
  return (
    <View style={[styles.chips, isLarge && styles.largeChips]}>
      {hashtags.map((hashtag) => (
        <SbPressable
          key={hashtag.keyword}
          accessibilityLabel={toAccessibilityLabel(hashtag)}
          hitSlop={isLarge ? undefined : SMALL_CHIP_HIT_SLOP}
          onPress={() => onHashtagPress(hashtag.keyword)}>
          <View style={[styles.chip, isLarge && styles.largeChip]}>
            <SbText variant={isLarge ? 'button' : 'caption'} color={colors.label} numberOfLines={sbMaximumLines.singleLine}>
              {`${HASH_SIGN}${hashtag.keyword}`}
            </SbText>
            {hashtag.countLabel !== undefined && (
              <SbText variant="capsLabel" color={colors.tertiaryLabel}>
                {hashtag.countLabel}
              </SbText>
            )}
          </View>
        </SbPressable>
      ))}
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    chips: { flexDirection: 'row', flexWrap: 'wrap', gap: sbSpacing.extraSmall },
    largeChips: { gap: sbSpacing.small },
    chip: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: sbSpacing.extraSmall,
      minHeight: SMALL_CHIP_HEIGHT,
      paddingHorizontal: sbSpacing.small,
      borderRadius: sbRadius.pill,
      backgroundColor: colors.surfaceRaised,
    },
    largeChip: { minHeight: LARGE_CHIP_HEIGHT, paddingHorizontal: sbSpacing.compact, gap: sbSpacing.small },
  });
