import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing, toSbArticleTitleColor } from '../tokens';
import { SbArticleImage, useSbArticleImageState } from './sb-article-image';
import { toSbArticleTitleMaximumLines } from './sb-article-image-layout';
import type { SbArticleHashtags } from './sb-article-row';
import { type SbArticleSecondaryLine, SbArticleSecondaryLineText } from './sb-article-secondary-line';
import { type SbBadgeContent, SbBadgeRow } from './sb-badge';
import { SbCvssGauge, type SbCvssGaugeValue } from './sb-cvss-gauge';
import { SbHashtagChips } from './sb-hashtag-chips';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

interface SbHeadlineCardProps {
  /** rail 가로 레일 안 카드(고정 너비), wide 화면 너비 카드(목록 대표 자리) */
  layout: 'rail' | 'wide';
  title: string;
  /** 제목 아래 작은 줄 (한 줄 결론 또는 번역 항목의 원문 제목) */
  secondaryLine?: SbArticleSecondaryLine;
  /** 게시자 · 짧은 날짜 (· AI 번역) 줄 */
  metaLabel: string;
  badges: SbBadgeContent[];
  /** 위쪽 대표 이미지 주소 (없거나 못 불러오면 그림 자리 없이 글만 있는 카드, 제목 전체) */
  imageUrl?: string;
  /** 그림 위에 얹는 CVSS 게이지 (그림이 없으면 글 위) */
  cvss?: SbCvssGaugeValue;
  hashtags?: SbArticleHashtags;
  /** 이미 읽은 소식 (제목을 흐리게) */
  isRead?: boolean;
  accessibilityLabel: string;
  onPress: () => void;
}

/**
 * 큰 소식 카드: (실제 이미지 + CVSS 게이지) > 배지 > 큰 제목 > (한 줄 결론 또는 원문 제목) > 게시자·날짜 > (해시태그)
 * 이미지가 없으면 글 카드: 위쪽 강조 띠 + CVSS 게이지를 글 위에, 제목 줄 제한 없음
 */
export function SbHeadlineCard({
  layout,
  title,
  secondaryLine,
  metaLabel,
  badges,
  imageUrl,
  cvss,
  hashtags,
  isRead = false,
  accessibilityLabel,
  onPress,
}: SbHeadlineCardProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const isRail = layout === 'rail';
  const { shownImageUrl, onImageFailed } = useSbArticleImageState(imageUrl);
  const hasImage = shownImageUrl !== undefined;
  // 해시태그는 카드 누름 영역 밖에 둠 (누를 수 있는 것 안에 또 누를 수 있는 것을 넣지 않음)
  return (
    <View style={[styles.card, isRail ? styles.railCard : styles.wideCard]}>
      <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress} feedback="dim" isFilling={isRail}>
        {hasImage && (
          <View style={isRail ? styles.railCover : styles.wideCover}>
            <SbArticleImage imageUrl={shownImageUrl} onImageFailed={onImageFailed} />
            {cvss !== undefined && (
              <View style={styles.gaugeOverlay}>
                <SbCvssGauge value={cvss} isBoxed />
              </View>
            )}
          </View>
        )}
        <View style={[styles.body, !hasImage && styles.textOnlyBody]}>
          {!hasImage && cvss !== undefined && <SbCvssGauge value={cvss} />}
          <SbBadgeRow badges={badges} />
          <SbText
            variant={isRail ? 'streamTitle' : 'headline'}
            color={toSbArticleTitleColor(colors, isRead)}
            numberOfLines={toSbArticleTitleMaximumLines(hasImage, sbMaximumLines.cardTitle)}>
            {title}
          </SbText>
          {secondaryLine !== undefined && <SbArticleSecondaryLineText secondaryLine={secondaryLine} />}
          <SbText variant="timeLabel" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
            {metaLabel}
          </SbText>
        </View>
      </SbPressable>
      {hashtags !== undefined && (
        <View style={styles.hashtags}>
          <SbHashtagChips
            hashtags={hashtags.hashtags}
            toAccessibilityLabel={hashtags.toAccessibilityLabel}
            onHashtagPress={hashtags.onHashtagPress}
          />
        </View>
      )}
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    card: { borderRadius: sbRadius.tile, overflow: 'hidden', backgroundColor: colors.surface },
    // 레일 카드는 옆 카드와 높이를 맞춤 (레일이 세로로 늘려 줌)
    railCard: { width: sbSize.railCardWidth },
    wideCard: { marginHorizontal: sbSpacing.tileHorizontal, marginBottom: sbSpacing.tileGap },
    railCover: { height: sbSize.railCoverHeight },
    wideCover: { height: sbSize.wideCoverHeight },
    gaugeOverlay: { position: 'absolute', left: sbSpacing.compact, bottom: sbSpacing.compact },
    body: { padding: sbSpacing.medium, gap: sbSpacing.small },
    // 글 카드: 위쪽 강조 띠로 큰 카드임을 표시 (그림 자리 대신)
    textOnlyBody: { borderTopWidth: sbSize.headlineAccentBarHeight, borderTopColor: colors.accent },
    hashtags: { paddingHorizontal: sbSpacing.medium, paddingBottom: sbSpacing.medium },
  });
