import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbArticleRowProps {
  /** 크게 보이는 제목 (번역이 있으면 한국어 제목) */
  title: string;
  /** 번역 항목의 원문 제목 (작게) */
  originalTitle?: string;
  /** 번역 표시 배지 문구 (화면이 넘김) */
  translationNoticeLabel?: string;
  /** 게시자 · 날짜 줄 */
  metaLabel: string;
  badgeLabels: string[];
  accessibilityLabel: string;
  onPress: () => void;
}

// 줄 수 제한: 제목 3줄, 원문 제목 2줄
const TITLE_MAXIMUM_LINES = 3;
const ORIGINAL_TITLE_MAXIMUM_LINES = 2;
const SEPARATOR_HEIGHT = StyleSheet.hairlineWidth;

/**
 * 소식 목록 행: 제목 > (원문 제목) > 게시자·날짜 > 배지 줄 > 아래 구분선
 * 디자인 계층은 문구를 갖지 않음 (모든 글은 화면이 넘김)
 */
export function SbArticleRow({
  title,
  originalTitle,
  translationNoticeLabel,
  metaLabel,
  badgeLabels,
  accessibilityLabel,
  onPress,
}: SbArticleRowProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress}>
      <View style={styles.row}>
        <SbText variant="streamTitle" color={colors.label} numberOfLines={TITLE_MAXIMUM_LINES}>
          {title}
        </SbText>
        {originalTitle !== undefined && (
          <SbText variant="caption" color={colors.secondaryLabel} numberOfLines={ORIGINAL_TITLE_MAXIMUM_LINES}>
            {originalTitle}
          </SbText>
        )}
        <SbText variant="timeLabel" color={colors.tertiaryLabel}>
          {metaLabel}
        </SbText>
        <View style={styles.badges}>
          {translationNoticeLabel !== undefined && (
            <View style={[styles.badge, styles.noticeBadge]}>
              <SbText variant="capsLabel" color={colors.onAccent}>
                {translationNoticeLabel}
              </SbText>
            </View>
          )}
          {badgeLabels.map((badgeLabel) => (
            <View key={badgeLabel} style={styles.badge}>
              <SbText variant="capsLabel" color={colors.secondaryLabel}>
                {badgeLabel}
              </SbText>
            </View>
          ))}
        </View>
      </View>
    </SbPressable>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    row: {
      paddingHorizontal: sbSpacing.pageHorizontal,
      paddingVertical: sbSpacing.compact,
      gap: sbSpacing.extraSmall,
      borderBottomWidth: SEPARATOR_HEIGHT,
      borderBottomColor: colors.separator,
    },
    badges: { flexDirection: 'row', flexWrap: 'wrap', gap: sbSpacing.extraSmall, paddingTop: sbSpacing.extraSmall },
    badge: {
      paddingHorizontal: sbSpacing.small,
      borderRadius: sbRadius.pill,
      backgroundColor: colors.surfaceRaised,
    },
    noticeBadge: { backgroundColor: colors.accent },
  });
