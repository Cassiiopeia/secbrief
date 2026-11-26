import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, type SbSeverityTone, sbMaximumLines, sbRadius, sbSpacing, toSbSeverityColor } from '../tokens';
import { SbText } from './sb-text';

/**
 * 배지 색조 5종: neutral 분류(회색), notice 새로 등장(민트), urgent 긴급(보라 채움), exploited 악용 중(본문색 채움), subject 회사·제품(테두리만)
 * + 심각도 4단계: critical, high, medium, low (심각도 색 테두리와 글자)
 */
export type SbBadgeTone = 'neutral' | 'notice' | 'urgent' | 'exploited' | 'subject' | SbSeverityTone;

export interface SbBadgeContent {
  label: string;
  tone: SbBadgeTone;
}

const BADGE_BORDER_WIDTH = 1;

/** 배지 한 개 (고정폭 작은 글자 알약) */
export function SbBadge({ label, tone }: SbBadgeContent) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={[styles.badge, styles[tone]]}>
      <SbText variant="capsLabel" color={toBadgeLabelColor(colors, tone)} numberOfLines={sbMaximumLines.singleLine}>
        {label}
      </SbText>
    </View>
  );
}

/** 배지 줄: 좁으면 다음 줄로 넘김. 배지가 없으면 아무것도 그리지 않음 */
export function SbBadgeRow({ badges }: { badges: SbBadgeContent[] }) {
  if (badges.length === 0) return null;
  return (
    <View style={rowStyles.row}>
      {badges.map((badge) => (
        <SbBadge key={`${badge.tone}-${badge.label}`} label={badge.label} tone={badge.tone} />
      ))}
    </View>
  );
}

/** 배지 색조 > 글자색 */
function toBadgeLabelColor(colors: SbColors, tone: SbBadgeTone): string {
  switch (tone) {
    case 'neutral':
      return colors.secondaryLabel;
    case 'notice':
      return colors.onAccent;
    case 'urgent':
      return colors.onFeature;
    case 'exploited':
      return colors.background;
    case 'subject':
      return colors.label;
    default:
      return toSbSeverityColor(colors, tone);
  }
}

const rowStyles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: sbSpacing.extraSmall },
});

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    badge: {
      maxWidth: '100%',
      paddingHorizontal: sbSpacing.small,
      paddingVertical: sbSpacing.hairline,
      borderRadius: sbRadius.pill,
      borderWidth: BADGE_BORDER_WIDTH,
    },
    neutral: { backgroundColor: colors.surfaceRaised, borderColor: colors.surfaceRaised },
    notice: { backgroundColor: colors.accent, borderColor: colors.accent },
    urgent: { backgroundColor: colors.feature, borderColor: colors.feature },
    exploited: { backgroundColor: colors.label, borderColor: colors.label },
    subject: { backgroundColor: colors.surface, borderColor: colors.tertiaryLabel },
    critical: { backgroundColor: colors.surface, borderColor: colors.severityCritical },
    high: { backgroundColor: colors.surface, borderColor: colors.severityHigh },
    medium: { backgroundColor: colors.surface, borderColor: colors.severityMedium },
    low: { backgroundColor: colors.surface, borderColor: colors.severityLow },
  });
