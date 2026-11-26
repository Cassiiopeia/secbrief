import { StyleSheet, View } from 'react-native';

import { useSbStyles, useSbTheme } from '../theme/use-sb-theme';
import { type SbColors, sbMaximumLines, sbRadius, sbSize, sbSpacing, toSbArticleTitleColor } from '../tokens';
import { SbArticleImage, useSbArticleImageState } from './sb-article-image';
import { toSbArticleTitleMaximumLines } from './sb-article-image-layout';
import { type SbArticleSecondaryLine, SbArticleSecondaryLineText } from './sb-article-secondary-line';
import { type SbBadgeContent, SbBadgeRow } from './sb-badge';
import { SbCvssGauge, type SbCvssGaugeValue } from './sb-cvss-gauge';
import { type SbHashtag, SbHashtagChips } from './sb-hashtag-chips';
import { SbPressable } from './sb-pressable';
import { SbText } from './sb-text';
import { SbArticleMetaLine } from './sb-translated-mark';

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
  /** 제목 아래 작은 줄 (한 줄 결론 또는 번역 항목의 원문 제목) */
  secondaryLine?: SbArticleSecondaryLine;
  /** 게시자 · 짧은 날짜 줄 */
  metaLabel: string;
  /** 번역 표시 읽기 도우미 문구 (있으면 메타 줄 끝에 번역 아이콘) */
  translatedMarkLabel?: string;
  badges: SbBadgeContent[];
  /** 왼쪽 CVSS 게이지 (점수가 있을 때) */
  cvss?: SbCvssGaugeValue;
  /** 오른쪽 썸네일 대표 이미지 주소 (없거나 못 불러오면 썸네일 없이 제목 전체) */
  imageUrl?: string;
  /** 카드에만 보이는 해시태그 */
  hashtags?: SbArticleHashtags;
  /** 이미 읽은 소식 (제목을 흐리게) */
  isRead?: boolean;
  accessibilityLabel: string;
  onPress: () => void;
}

/**
 * 소식 행 2종: 중간 카드(주목할 소식)와 조밀한 행(그 밖). 배치는 (CVSS 게이지) + 글 + (실제 이미지 썸네일)
 * 이미지가 없으면 썸네일 자리 없이 제목을 줄 제한 없이 보여 줌
 * 디자인 계층은 문구를 갖지 않음 (모든 글은 화면이 넘김)
 */
export function SbArticleRow({
  density,
  title,
  secondaryLine,
  metaLabel,
  translatedMarkLabel,
  badges,
  cvss,
  imageUrl,
  hashtags,
  isRead = false,
  accessibilityLabel,
  onPress,
}: SbArticleRowProps) {
  const { colors } = useSbTheme();
  const styles = useSbStyles(createStyles);
  const isCard = density === 'card';
  const { shownImageUrl, onImageFailed } = useSbArticleImageState(imageUrl);
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
                numberOfLines={toSbArticleTitleMaximumLines(
                  shownImageUrl !== undefined,
                  isCard ? sbMaximumLines.cardTitle : sbMaximumLines.compactTitle,
                )}>
                {title}
              </SbText>
              {secondaryLine !== undefined && <SbArticleSecondaryLineText secondaryLine={secondaryLine} />}
              <SbArticleMetaLine metaLabel={metaLabel} translatedMarkLabel={translatedMarkLabel} />
              {!isCard && <SbBadgeRow badges={badges} />}
            </View>
            {shownImageUrl !== undefined && (
              <View style={[styles.thumbnail, !isCard && styles.compactThumbnail]}>
                <SbArticleImage imageUrl={shownImageUrl} onImageFailed={onImageFailed} />
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
