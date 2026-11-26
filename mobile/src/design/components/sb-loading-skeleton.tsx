import { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withRepeat, withTiming } from 'react-native-reanimated';

import { useIsMotionReduced } from '../theme/use-is-motion-reduced';
import { useSbStyles } from '../theme/use-sb-theme';
import { type SbColors, sbMotion, sbRadius, sbSize, sbSpacing } from '../tokens';

/**
 * 뼈대 배치 5종 (불러온 뒤 나올 내용과 같은 자리): briefing 오늘 탭(상태 띠 > 큰 타일 > 통계 타일 > 스트림), articleRows 소식 행(제목 두 줄 + 메타 + 썸네일),
 * listRows 설정·동향 행(제목 + 설명), articleDetail 기사 상세(그림 > 제목 > 카드), briefCards 기사 상세 브리프(카드만)
 */
export type SbLoadingSkeletonVariant = 'briefing' | 'articleRows' | 'listRows' | 'articleDetail' | 'briefCards';

interface SbLoadingSkeletonProps {
  variant?: SbLoadingSkeletonVariant;
  /** 행 배치에서 그릴 행 수 */
  rowCount?: number;
}

// 뼈대 블록 높이: 상태 띠, 긴급 타일, 통계 타일, 스트림 한 줄, 브리프 카드, 글 한 줄(상세 제목, 제목, 보조)
const BAND_BLOCK_HEIGHT = 64;
const URGENT_BLOCK_HEIGHT = 220;
const STAT_BLOCK_HEIGHT = 104;
const STREAM_LINE_HEIGHT = 44;
const STREAM_LINE_COUNT = 3;
const BRIEF_CARD_HEIGHT = 132;
const BRIEF_CARD_COUNT = 2;
const DETAIL_TITLE_BAR_HEIGHT = 22;
const TITLE_BAR_HEIGHT = 16;
const DETAIL_BAR_HEIGHT = 11;
const DEFAULT_ROW_COUNT = 5;

/** 로딩 뼈대: 불러올 내용과 같은 자리에 회색 블록이 은은하게 깜박임. 동작 줄이기가 켜져 있으면 깜박이지 않음 */
export function SbLoadingSkeleton({ variant = 'briefing', rowCount = DEFAULT_ROW_COUNT }: SbLoadingSkeletonProps) {
  const isMotionReduced = useIsMotionReduced();
  const opacity = useSharedValue(1);
  useEffect(() => {
    if (isMotionReduced) return;
    opacity.set(
      withRepeat(
        withTiming(sbMotion.skeletonDimmestOpacity, { duration: sbMotion.skeletonMilliseconds, easing: sbMotion.skeletonEasing }),
        -1, // 끝없이 반복
        true, // 되돌아오며 반복
      ),
    );
  }, [isMotionReduced, opacity]);
  const animatedStyle = useAnimatedStyle(() => ({ opacity: opacity.value }));
  return (
    <Animated.View style={animatedStyle} accessibilityRole="progressbar" accessibilityLabel="불러오는 중이에요">
      {variant === 'briefing' && <BriefingSkeleton />}
      {(variant === 'articleDetail' || variant === 'briefCards') && <ArticleDetailSkeleton hasHeading={variant === 'articleDetail'} />}
      {(variant === 'articleRows' || variant === 'listRows') && <RowsSkeleton hasThumbnail={variant === 'articleRows'} rowCount={rowCount} />}
    </Animated.View>
  );
}

/** 오늘 탭 뼈대: 상태 띠 > 큰 타일 > 통계 타일 2개 > 스트림 3줄 */
function BriefingSkeleton() {
  const styles = useSbStyles(createStyles);
  return (
    <View style={styles.tiles}>
      <View style={[styles.block, styles.band]} />
      <View style={[styles.block, styles.urgent]} />
      <View style={styles.stats}>
        <View style={[styles.block, styles.stat]} />
        <View style={[styles.block, styles.stat]} />
      </View>
      {Array.from({ length: STREAM_LINE_COUNT }, (_, lineIndex) => (
        <View key={lineIndex} style={[styles.block, styles.streamLine]} />
      ))}
    </View>
  );
}

/** 기사 상세 뼈대: (그림 > 제목 두 줄 + 메타) > 브리프 카드 2장 */
function ArticleDetailSkeleton({ hasHeading }: { hasHeading: boolean }) {
  const styles = useSbStyles(createStyles);
  return (
    <View>
      {hasHeading && (
        <>
          <View style={styles.tiles}>
            <View style={[styles.block, styles.cover]} />
          </View>
          <View style={styles.heading}>
            <View style={[styles.bar, styles.detailTitleBar]} />
            <View style={[styles.bar, styles.detailTitleBar, styles.shortTitleBar]} />
            <View style={[styles.bar, styles.detailBar]} />
          </View>
        </>
      )}
      <View style={styles.tiles}>
        {Array.from({ length: BRIEF_CARD_COUNT }, (_, cardIndex) => (
          <View key={cardIndex} style={[styles.block, styles.briefCard]} />
        ))}
      </View>
    </View>
  );
}

/** 행 뼈대: 제목 (+ 짧은 둘째 줄) + 보조 줄 (+ 썸네일) */
function RowsSkeleton({ hasThumbnail, rowCount }: { hasThumbnail: boolean; rowCount: number }) {
  const styles = useSbStyles(createStyles);
  return Array.from({ length: rowCount }, (_, rowIndex) => (
    <View key={rowIndex} style={styles.row}>
      <View style={styles.rowText}>
        <View style={[styles.bar, styles.titleBar]} />
        {hasThumbnail && <View style={[styles.bar, styles.titleBar, styles.shortTitleBar]} />}
        <View style={[styles.bar, styles.detailBar]} />
      </View>
      {hasThumbnail && <View style={[styles.block, styles.thumbnail]} />}
    </View>
  ));
}

const createStyles = (colors: SbColors) =>
  StyleSheet.create({
    tiles: { paddingHorizontal: sbSpacing.tileHorizontal, gap: sbSpacing.tileGap },
    stats: { flexDirection: 'row', gap: sbSpacing.tileGap },
    block: { backgroundColor: colors.skeleton, borderRadius: sbRadius.tile },
    band: { height: BAND_BLOCK_HEIGHT },
    urgent: { height: URGENT_BLOCK_HEIGHT },
    stat: { flex: 1, height: STAT_BLOCK_HEIGHT },
    streamLine: { height: STREAM_LINE_HEIGHT, borderRadius: sbRadius.thumbnail },
    cover: { height: sbSize.wideCoverHeight },
    heading: { gap: sbSpacing.small, paddingHorizontal: sbSpacing.pageHorizontal, paddingVertical: sbSpacing.medium },
    briefCard: { height: BRIEF_CARD_HEIGHT },
    row: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: sbSpacing.compact,
      marginHorizontal: sbSpacing.pageHorizontal,
      paddingVertical: sbSpacing.rowVertical,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: colors.separator,
    },
    rowText: { flex: 1, gap: sbSpacing.small },
    bar: { backgroundColor: colors.skeleton, borderRadius: sbRadius.pill },
    detailTitleBar: { height: DETAIL_TITLE_BAR_HEIGHT, width: '92%' },
    titleBar: { height: TITLE_BAR_HEIGHT, width: '92%' },
    shortTitleBar: { width: '64%' },
    detailBar: { height: DETAIL_BAR_HEIGHT, width: '42%' },
    thumbnail: { width: sbSize.articleThumbnail, height: sbSize.articleThumbnail, borderRadius: sbRadius.thumbnail },
  });
