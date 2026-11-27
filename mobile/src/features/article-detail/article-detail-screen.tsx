import { useLocalSearchParams } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { type LayoutChangeEvent, Platform, ScrollView, type ScrollViewInstance, Share, StyleSheet, View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import {
  SbArticleImage,
  SbBadgeRow,
  SbBottomActionBar,
  SbCvssGauge,
  SbEmptyState,
  SbErrorState,
  SbFilledButton,
  SbHashtagChips,
  SbIconButton,
  SbLoadingSkeleton,
  SbScreen,
  SbScreenHeader,
  SbText,
  SbTranslatedMark,
  sbRadius,
  sbSize,
  sbSpacing,
  useSbArticleImageState,
  useSbTheme,
} from '@/design';

import { type ArticleRowView, toBadgesWithoutImplied } from '../articles/article-row-view';
import { copyOriginalArticleLink } from '../articles/copy-original-article-link';
import { openOriginalArticle } from '../articles/open-original-article';
import { useMarkArticleRead } from '../articles/use-read-articles';
import { toHashtagAccessibilityLabel } from '../keywords/keyword-view-state';
import { openKeywordArticles } from '../keywords/open-keyword-articles';
import { ARTICLE_DETAIL_FOCUSES, type ArticleDetailFocus } from '../navigation/app-routes';
import { goBackOrHome } from '../navigation/go-back-or-home';
import { ArticleDetailBrief } from './article-detail-brief';
import { toArticleShareContent, toOriginalButtonAccessibilityLabel, toOriginalButtonLabel } from './article-detail-view';
import { useArticleDetail } from './use-article-detail';

const BACK_ACCESSIBILITY_LABEL = '뒤로';
// 내려간 구역 머리가 화면 맨 위에 딱 붙지 않게 남기는 여백
const FOCUS_SCROLL_TOP_INSET = sbSpacing.medium;

/**
 * 기사 상세 화면 (탭 밖, 앱 안 브리프): (실제 대표 이미지) > 배지·CVSS > 제목 > 출처·게시일 > 브리프(AI 브리핑·확인된 정보·대응 모아보기)
 * 아래 고정 줄: 원문 보기 > 원문 링크 복사 > 공유(시큐브리프 공유 링크). 조회가 실패해도 넘겨받은 기본 정보와 원문 보기는 남음
 * ?focus=responseGuide 로 열면 대응 모아보기가 그려진 뒤 한 번 그 위치로 내려감 (알림 상세에서 들어옴)
 */
export function ArticleDetailScreen() {
  const { articleId, focus } = useLocalSearchParams<{ articleId: string; focus?: string }>();
  const { detailQuery, article } = useArticleDetail(articleId ?? '');
  const markArticleRead = useMarkArticleRead();
  const { scrollViewRef, onBriefLayout, onTargetLayout } = useFocusScroll(toArticleDetailFocus(focus));

  // 화면을 연 것으로 읽음 처리 (기기에만 저장)
  useEffect(() => {
    if (articleId) markArticleRead(articleId);
  }, [articleId, markArticleRead]);

  return (
    <SbScreen>
      <SbScreenHeader title={article?.kindLabel} backAccessibilityLabel={BACK_ACCESSIBILITY_LABEL} onBackPress={goBackOrHome} />
      <ScrollView ref={scrollViewRef} contentContainerStyle={styles.content}>
        {article !== undefined && <ArticleDetailHeading article={article} />}
        <View onLayout={onBriefLayout}>{renderBrief()}</View>
      </ScrollView>
      {article !== undefined && (
        <SbBottomActionBar
          primaryAction={
            <SbFilledButton
              label={toOriginalButtonLabel(article)}
              accessibilityLabel={toOriginalButtonAccessibilityLabel(article)}
              trailingIconName="open-in-new"
              onPress={() => openOriginalArticle(article.originalUrl)}
            />
          }
          secondaryAction={
            <>
              <SbIconButton
                iconName="link"
                accessibilityLabel="원문 링크 복사"
                onPress={() => void copyOriginalArticleLink(article.originalUrl)}
              />
              <SbIconButton
                iconName="ios-share"
                accessibilityLabel="공유하기"
                onPress={() => void Share.share(toArticleShareContent(article, Platform.OS)).catch(() => undefined)}
              />
            </>
          }
        />
      )}
    </SbScreen>
  );

  /** 브리프 구역: 로딩 뼈대(넘겨받은 머리가 없으면 머리 자리까지) / 오류(에러 코드, 원문 보기는 아래 줄에 계속) / 브리프 */
  function renderBrief() {
    if (detailQuery.isPending) return <SbLoadingSkeleton variant={article === undefined ? 'articleDetail' : 'briefCards'} />;
    if (detailQuery.isError) {
      const failure = toAppFailure(detailQuery.error);
      return <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => detailQuery.refetch()} />;
    }
    if (article === undefined) return <SbEmptyState title="소식을 찾지 못했어요" description="이전 화면에서 다시 골라 주세요." />;
    return <ArticleDetailBrief detail={detailQuery.data} onResponseGuideLayout={onTargetLayout} />;
  }
}

/** ?focus= 값 > 내려갈 구역 (모르는 값은 없음) */
function toArticleDetailFocus(focus: string | undefined): ArticleDetailFocus | undefined {
  return ARTICLE_DETAIL_FOCUSES.find((knownFocus) => knownFocus === focus);
}

/**
 * 구역으로 한 번 내려가기: 브리프 구역 위치 + 구역 안 위치 > 스크롤 (둘 다 잰 뒤 1회, 다시 그려져도 반복하지 않음)
 * focus 가 없으면 아무것도 하지 않음
 */
function useFocusScroll(focus: ArticleDetailFocus | undefined) {
  const scrollViewRef = useRef<ScrollViewInstance>(null);
  const hasScrolledRef = useRef(false);
  const [briefOffset, setBriefOffset] = useState<number>();
  const [targetOffset, setTargetOffset] = useState<number>();

  useEffect(() => {
    if (focus === undefined || hasScrolledRef.current || briefOffset === undefined || targetOffset === undefined) return;
    hasScrolledRef.current = true;
    scrollViewRef.current?.scrollTo({ y: Math.max(briefOffset + targetOffset - FOCUS_SCROLL_TOP_INSET, 0), animated: true });
  }, [focus, briefOffset, targetOffset]);

  return {
    scrollViewRef,
    onBriefLayout: (event: LayoutChangeEvent) => setBriefOffset(event.nativeEvent.layout.y),
    onTargetLayout: (event: LayoutChangeEvent) => setTargetOffset(event.nativeEvent.layout.y),
  };
}

/**
 * 상세 머리: (대표 이미지 + CVSS 게이지) > 배지(종류는 화면 제목에 있어 생략) > 제목 > (번역 아이콘 + 원문 제목) > 출처·게시일 > 해시태그
 * 이미지가 없거나 못 불러오면 그림 자리 없이 배지부터 (CVSS 게이지는 배지 위)
 */
function ArticleDetailHeading({ article }: { article: ArticleRowView }) {
  const { colors } = useSbTheme();
  const { shownImageUrl, onImageFailed } = useSbArticleImageState(article.imageUrl);
  return (
    <View>
      {shownImageUrl !== undefined && (
        <View style={styles.cover}>
          <SbArticleImage imageUrl={shownImageUrl} onImageFailed={onImageFailed} />
          {article.cvss !== undefined && (
            <View style={styles.gauge}>
              <SbCvssGauge value={article.cvss} isBoxed />
            </View>
          )}
        </View>
      )}
      <View style={styles.heading}>
        {shownImageUrl === undefined && article.cvss !== undefined && <SbCvssGauge value={article.cvss} />}
        <SbBadgeRow badges={toBadgesWithoutImplied(article.badges, article.kindLabel)} />
        <View accessibilityRole="header">
          <SbText variant="detailTitle" color={colors.label}>
            {article.title}
          </SbText>
        </View>
        {article.originalTitle !== undefined && (
          <View style={styles.originalTitle}>
            {article.translatedMarkLabel !== undefined && <SbTranslatedMark accessibilityLabel={article.translatedMarkLabel} />}
            <View style={styles.originalTitleText}>
              <SbText variant="caption" color={colors.tertiaryLabel}>
                {article.originalTitle}
              </SbText>
            </View>
          </View>
        )}
        <SbText variant="timeLabel" color={colors.tertiaryLabel}>
          {article.metaLabel}
        </SbText>
        <SbHashtagChips hashtags={article.hashtags} toAccessibilityLabel={toHashtagAccessibilityLabel} onHashtagPress={openKeywordArticles} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  // 오류 안내가 남은 높이 가운데에 오도록
  content: { flexGrow: 1, paddingBottom: sbSpacing.large },
  cover: {
    height: sbSize.wideCoverHeight,
    marginHorizontal: sbSpacing.tileHorizontal,
    borderRadius: sbRadius.tile,
    overflow: 'hidden',
  },
  gauge: { position: 'absolute', left: sbSpacing.compact, bottom: sbSpacing.compact },
  // 번역 아이콘 + 원문 제목 (아이콘은 첫 줄 높이에 맞춤)
  originalTitle: { flexDirection: 'row', alignItems: 'flex-start', gap: sbSpacing.extraSmall },
  originalTitleText: { flex: 1 },
  heading: { gap: sbSpacing.small, paddingHorizontal: sbSpacing.pageHorizontal, paddingVertical: sbSpacing.medium },
});
