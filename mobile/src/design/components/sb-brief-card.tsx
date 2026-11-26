import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbRadius, sbSpacing } from '../tokens';
import type { SbIconName } from './sb-bottom-tab-bar';
import { SbText } from './sb-text';

interface SbBriefCardProps {
  title: string;
  iconName: SbIconName;
  /** 제목 오른쪽 작은 표시 (예: AI 가 요약했어요) */
  noticeLabel?: string;
  /** emphasis 는 눈에 띄어야 하는 카드 (대응 안내): 강조색 테두리 */
  tone?: 'plain' | 'emphasis';
  children: ReactNode;
}

const TITLE_ICON_SIZE = 18;
const NOTICE_ICON_SIZE = 12;
const EMPHASIS_BORDER_WIDTH = 2;

/** 브리프 카드 (기사 상세의 요약·핵심·대응·확인된 정보 구역): 아이콘 + 제목 + (작은 AI 표시) > 내용 */
export function SbBriefCard({ title, iconName, noticeLabel, tone = 'plain', children }: SbBriefCardProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.wrap}>
      <View style={[styles.card, tone === 'emphasis' && styles.emphasis]}>
        <View style={styles.header}>
          <MaterialIcons name={iconName} size={TITLE_ICON_SIZE} color={colors.label} />
          <View style={styles.title} accessibilityRole="header">
            <SbText variant="sectionTitle" color={colors.label}>
              {title}
            </SbText>
          </View>
          {noticeLabel !== undefined && (
            <View style={styles.notice}>
              <MaterialIcons name="auto-awesome" size={NOTICE_ICON_SIZE} color={colors.tertiaryLabel} />
              <SbText variant="caption" color={colors.tertiaryLabel}>
                {noticeLabel}
              </SbText>
            </View>
          )}
        </View>
        {children}
      </View>
    </View>
  );
}

/** 브리프 카드 안 긴 글 (요약·대응): 읽기 편한 줄 간격의 본문 */
export function SbBriefParagraph({ text }: { text: string }) {
  const { colors } = useSbTheme();
  return (
    <SbText variant="reading" color={colors.secondaryLabel}>
      {text}
    </SbText>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    wrap: { paddingHorizontal: sbSpacing.tileHorizontal, paddingBottom: sbSpacing.tileGap },
    card: { gap: sbSpacing.compact, padding: sbSpacing.medium, borderRadius: sbRadius.tile, backgroundColor: colors.surface },
    emphasis: { borderWidth: EMPHASIS_BORDER_WIDTH, borderColor: colors.accent },
    header: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.small },
    title: { flex: 1 },
    notice: { flexDirection: 'row', alignItems: 'center', gap: sbSpacing.extraSmall },
  });
