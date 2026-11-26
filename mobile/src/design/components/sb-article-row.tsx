import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing, toSbArticleTitleColor } from '../tokens';
import { SbArticleImage } from './sb-article-image';
import { type SbBadgeContent, SbBadgeRow } from './sb-badge';
import type { SbCoverKind, SbCoverTone } from './sb-cover-art';
import { SbCvssGauge, type SbCvssGaugeValue } from './sb-cvss-gauge';
import { type SbHashtag, SbHashtagChips } from './sb-hashtag-chips';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';

/** 소식 그림: 대표 이미지(있으면) + 대신 보일 커버 그림 */
export interface SbArticleThumbnail {
  imageUrl?: string;
  coverKind: SbCoverKind;
  coverTone: SbCoverTone;
}

/** 해시태그 칩 묶음 (칩 + 읽기 도우미 문구 + 누름) */
export interface SbArticleHashtags {
  hashtags: SbHashtag[];
  toAccessibilityLabel: (hashtag: SbHashtag) => string;
  onHashtagPress: (keyword: string) => void;
}

interface SbArticleRowProps {
  /** card 중간 카드(표면색 상자, 배지·해시태그), compact 조밀한 행(제목 2줄 + 메타 한 줄) */
  density: 'card' | 'compact';
  /** 크게 보이는 제목 (번역이 있으면 한국어 제목) */
  title: string;
  /** 번역 항목의 원문 제목 (작게) */
  originalTitle?: string;
  /** 게시자 · 짧은 날짜 (· AI 번역) 줄 */
  metaLabel: string;
  badges: SbBadgeContent[];
  /** 왼쪽 CVSS 게이지 (점수가 있을 때) */
  cvss?: SbCvssGaugeValue;
  /** 오른쪽 썸네일 (없으면 글만) */
  thumbnail?: SbArticleThumbnail;
  /** 카드에만 보이는 해시태그 */
  hashtags?: SbArticleHashtags;
  /** 이미 읽은 소식 (제목을 흐리게) */
  isRead?: boolean;
  accessibilityLabel: string;
  onPress: () => void;
}

/**
 * 소식 행 2종: 중간 카드(주목할 소식)와 조밀한 행(그 밖). 배치는 (CVSS 게이지) + 글 + (썸네일)
 * 디자인 계층은 문구를 갖지 않음 (모든 글은 화면이 넘김)
 */
export function SbArticleRow({
  density,
  title,
  originalTitle,
  metaLabel,
  badges,
  cvss,
  thumbnail,
  hashtags,
  isRead = false,
  accessibilityLabel,
  onPress,
}: SbArticleRowProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const isCard = density === 'card';
  // 해시태그는 누름 영역 밖에 둠 (누를 수 있는 것 안에 또 누를 수 있는 것을 넣지 않음)
  return (
    <View style={isCard ? styles.cardWrap : styles.compactWrap}>
      <View style={isCard ? styles.card : styles.compact}>
        <SbPressable accessibilityLabel={accessibilityLabel} onPress={onPress} feedback="dim">
          <View style={styles.content}>
            {cvss !== undefined && <SbCvssGauge value={cvss} />}
            <View style={styles.text}>
              {isCard && <SbBadgeRow badges={badges} />}
              <SbText
                variant="streamTitle"
                color={toSbArticleTitleColor(colors, isRead)}
                numberOfLines={isCard ? sbMaximumLines.cardTitle : sbMaximumLines.compactTitle}>
                {title}
              </SbText>
              {originalTitle !== undefined && (
                <SbText variant="caption" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
                  {originalTitle}
                </SbText>
              )}
              <SbText variant="timeLabel" color={colors.tertiaryLabel} numberOfLines={sbMaximumLines.singleLine}>
                {metaLabel}
              </SbText>
              {!isCard && <SbBadgeRow badges={badges} />}
            </View>
            {thumbnail !== undefined && (
              <View style={[styles.thumbnail, !isCard && styles.compactThumbnail]}>
                <SbArticleImage imageUrl={thumbnail.imageUrl} coverKind={thumbnail.coverKind} coverTone={thumbnail.coverTone} />
              </View>
            )}
          </View>
        </SbPressable>
        {isCard && hashtags !== undefined && (
          <SbHashtagChips
            hashtags={hashtags.hashtags}
            toAccessibilityLabel={hashtags.toAccessibilityLabel}
            onHashtagPress={hashtags.onHashtagPress}
          />
        )}
      </View>
    </View>
  );
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    cardWrap: { paddingHorizontal: sbSpacing.tileHorizontal, paddingBottom: sbSpacing.tileGap },
    compactWrap: { paddingHorizontal: sbSpacing.pageHorizontal },
    content: { flexDirection: 'row', alignItems: 'flex-start', gap: sbSpacing.compact },
    card: { gap: sbSpacing.compact, padding: sbSpacing.medium, borderRadius: sbRadius.tile, backgroundColor: colors.surface },
    // 구분선은 글 시작 위치에 맞춤 (화면 끝까지 긋지 않음)
    compact: {
      paddingVertical: sbSpacing.compact,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: colors.separator,
    },
    text: { flex: 1, gap: sbSpacing.extraSmall },
    thumbnail: {
      width: sbSize.articleThumbnail,
      height: sbSize.articleThumbnail,
      borderRadius: sbRadius.thumbnail,
      overflow: 'hidden',
      backgroundColor: colors.surfaceRaised,
    },
    compactThumbnail: { width: sbSize.compactThumbnail, height: sbSize.compactThumbnail },
  });
