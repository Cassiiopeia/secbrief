import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { SbBriefParagraph, SbCapsLabel, type SbColors, SbNoticeMark, SbNumberedList, SbText, sbRadius, sbSpacing, useSbStyles, useSbTheme } from '@/design';

import { type AffectedTargetView, type ArticleBriefingView, BRIEFING_SECTION_TITLES } from './article-briefing-view';

/**
 * AI 브리핑 카드 (기사 상세): 보라 띠의 한 줄 결론(AI 표시 1회) > 무슨 일이에요 > 왜 중요해요 > 영향을 받는 대상 > 이렇게 대응해요
 * 카드 안 글은 모두 AI 가 쓴 것. 값이 없는 구역은 숨기고, 대응이 없으면 원문 안내 한 줄
 */
export function ArticleBriefingCard({ briefing }: { briefing: ArticleBriefingView }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.wrap}>
      <View style={styles.card}>
        <View style={styles.lead}>
          <View style={styles.leadHeader}>
            <View style={styles.leadTitle} accessibilityRole="header">
              <SbCapsLabel text={BRIEFING_SECTION_TITLES.oneLine} color={colors.onFeature} />
            </View>
            <SbNoticeMark label={briefing.aiNoticeLabel} color={colors.onFeature} />
          </View>
          <SbText variant="briefLead" color={colors.onFeature}>
            {briefing.oneLine}
          </SbText>
        </View>
        <View style={styles.sections}>
          {briefing.whatHappened !== undefined && (
            <BriefingSection title={BRIEFING_SECTION_TITLES.whatHappened}>
              <SbBriefParagraph text={briefing.whatHappened} />
            </BriefingSection>
          )}
          {briefing.whyItMatters !== undefined && (
            <View style={styles.callout}>
              <BriefingSectionTitle title={BRIEFING_SECTION_TITLES.whyItMatters} color={colors.onAccent} />
              <SbText variant="reading" color={colors.onAccent}>
                {briefing.whyItMatters}
              </SbText>
            </View>
          )}
          {briefing.affectedTargets.length > 0 && (
            <BriefingSection title={BRIEFING_SECTION_TITLES.affectedTargets}>
              <AffectedTargetList affectedTargets={briefing.affectedTargets} />
            </BriefingSection>
          )}
          <BriefingSection title={BRIEFING_SECTION_TITLES.actions}>
            {briefing.emptyActionMessage === undefined ? (
              <SbNumberedList items={briefing.actions} />
            ) : (
              <SbText variant="body" color={colors.tertiaryLabel}>
                {briefing.emptyActionMessage}
              </SbText>
            )}
          </BriefingSection>
        </View>
      </View>
    </View>
  );
}

/** 브리핑 구역: 구역 제목(머리글 역할) > 내용 */
function BriefingSection({ title, children }: { title: string; children: ReactNode }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.section}>
      <BriefingSectionTitle title={title} color={colors.label} />
      {children}
    </View>
  );
}

function BriefingSectionTitle({ title, color }: { title: string; color: string }) {
  return (
    <View accessibilityRole="header">
      <SbText variant="sectionTitle" color={color}>
        {title}
      </SbText>
    </View>
  );
}

/** 영향을 받는 대상 목록: 대상 이름(굵게) > 버전 범위·조건(고정폭, 서버 값 그대로). 줄 사이 구분선 */
function AffectedTargetList({ affectedTargets }: { affectedTargets: AffectedTargetView[] }) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View role="list">
      {affectedTargets.map((affectedTarget, targetIndex) => (
        <View
          key={`${targetIndex}-${affectedTarget.target}`}
          style={[styles.affectedTarget, targetIndex > 0 && styles.affectedTargetSeparator]}
          role="listitem"
          accessible
          accessibilityLabel={affectedTarget.accessibilityLabel}>
          <SbText variant="rowTitle" color={colors.label}>
            {affectedTarget.target}
          </SbText>
          {affectedTarget.detail !== undefined && (
            <SbText variant="factValue" color={colors.secondaryLabel}>
              {affectedTarget.detail}
            </SbText>
          )}
        </View>
      ))}
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { paddingHorizontal: sbSpacing.tileHorizontal, paddingBottom: sbSpacing.tileGap },
    card: { borderRadius: sbRadius.tile, overflow: 'hidden', backgroundColor: colors.surface },
    lead: { gap: sbSpacing.small, padding: sbSpacing.medium, backgroundColor: colors.feature },
    leadHeader: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.small },
    leadTitle: { flex: 1 },
    sections: { gap: sbSpacing.large, padding: sbSpacing.medium },
    section: { gap: sbSpacing.small },
    callout: { gap: sbSpacing.extraSmall, padding: sbSpacing.compact, borderRadius: sbRadius.button, backgroundColor: colors.accent },
    affectedTarget: { gap: sbSpacing.hairline },
    // 둘째 줄부터 위 구분선 (첫 줄은 구역 제목 바로 아래에 붙음)
    affectedTargetSeparator: {
      marginTop: sbSpacing.small,
      paddingTop: sbSpacing.small,
      borderTopWidth: StyleSheet.hairlineWidth,
      borderTopColor: colors.separator,
    },
  });
