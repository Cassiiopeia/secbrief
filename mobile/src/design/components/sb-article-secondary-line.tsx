import MaterialIcons from '@expo/vector-icons/MaterialIcons';
import { StyleSheet, View } from 'react-native';

import { useSbTheme } from '../theme/use-sb-theme';
import { sbMaximumLines, sbSpacing, sbTypography } from '../tokens';
import { SbText } from './sb-text';

/**
 * 소식 제목 아래 작은 줄 2종: summary AI 한 줄 결론(반짝 아이콘 + 2줄까지, 보조 글자색), reference 원문 제목(한 줄, 흐린 글자색)
 */
export interface SbArticleSecondaryLine {
  text: string;
  tone: 'summary' | 'reference';
}

const SUMMARY_ICON_SIZE = 12;

/** 소식 제목 아래 작은 줄 (중간 카드·조밀한 행·큰 카드 공통) */
export function SbArticleSecondaryLineText({ secondaryLine }: { secondaryLine: SbArticleSecondaryLine }) {
  const { colors } = useSbTheme();
  if (secondaryLine.tone === 'reference') {
    return (
      <SbText variant="caption" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
        {secondaryLine.text}
      </SbText>
    );
  }
  return (
    <View style={styles.summary}>
      <View style={styles.summaryIcon}>
        <MaterialIcons name="auto-awesome" size={SUMMARY_ICON_SIZE} color={colors.tertiaryLabel} />
      </View>
      <View style={styles.summaryText}>
        <SbText variant="caption" color={colors.secondaryLabel} numberOfLines={sbMaximumLines.articleSummary}>
          {secondaryLine.text}
        </SbText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  summary: { flexDirection: 'row', alignItems: 'flex-start', gap: sbSpacing.extraSmall },
  // 아이콘을 글 첫 줄 높이 가운데에 맞춤 (글이 2줄이어도 첫 줄 옆에 있게)
  summaryIcon: { height: sbTypography.caption.lineHeight, justifyContent: 'center' },
  summaryText: { flex: 1 },
});
