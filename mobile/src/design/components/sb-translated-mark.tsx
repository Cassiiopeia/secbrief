import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View, useWindowDimensions } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbMaximumFontScale, sbMaximumLines, sbSpacing, sbTypography } from '../tokens';
import { SbText } from './sb-text';

// 번역 표시 아이콘 크기: 메타 줄 글자(timeLabel)보다 조금 크게 (글자 확대 한도까지 함께 커짐)
const TRANSLATED_MARK_SIZE_RATIO = 1.25;

interface SbTranslatedMarkProps {
  /** 읽기 도우미 문구 (예: AI가 번역한 제목이에요) */
  accessibilityLabel: string;
  /** 아이콘 색 (기본: 옅은 글자색) */
  color?: string;
}

/** AI 번역 표시: 번역 아이콘 하나 (글자 없음). 카드·행·상세에 한 번만 */
export function SbTranslatedMark({ accessibilityLabel, color }: SbTranslatedMarkProps) {
  const { colors } = useSbTheme();
  const { fontScale } = useWindowDimensions();
  const iconSize = Math.round(sbTypography.timeLabel.fontSize * TRANSLATED_MARK_SIZE_RATIO * Math.min(fontScale, sbMaximumFontScale.compact));
  return (
    <View accessible accessibilityRole="image" accessibilityLabel={accessibilityLabel}>
      <MaterialIcons name="translate" size={iconSize} color={color ?? colors.tertiaryLabel} />
    </View>
  );
}

interface SbArticleMetaLineProps {
  /** 게시자 · 날짜 줄 */
  metaLabel: string;
  /** 번역 표시 읽기 도우미 문구 (있으면 줄 끝에 번역 아이콘, 없으면 글만) */
  translatedMarkLabel?: string;
}

/** 소식 메타 줄: 게시자 · 날짜 + (번역 아이콘) (카드·행 공통) */
export function SbArticleMetaLine({ metaLabel, translatedMarkLabel }: SbArticleMetaLineProps) {
  const { colors } = useSbTheme();
  return (
    <View style={styles.line}>
      <View style={styles.label}>
        <SbText variant="timeLabel" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
          {metaLabel}
        </SbText>
      </View>
      {translatedMarkLabel !== undefined && <SbTranslatedMark accessibilityLabel={translatedMarkLabel} />}
    </View>
  );
}

const styles = StyleSheet.create({
  line: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.extraSmall },
  // 긴 게시자 이름은 줄여도 아이콘은 남게
  label: { flexShrink: 1 },
});
