import { useLocalSearchParams } from 'expo-router';
import { useEffect } from 'react';
import { ScrollView, Share, StyleSheet, View } from 'react-native';

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
  sbRadius,
  sbSize,
  sbSpacing,
  useSbTheme,
} from '@/design';

import { type ArticleRowView, toBadgesWithoutImplied } from '../articles/article-row-view';
import { openOriginalArticle } from '../articles/open-original-article';
import { useMarkArticleRead } from '../articles/use-read-articles';
import { toHashtagAccessibilityLabel } from '../keywords/keyword-view-state';
import { openKeywordArticles } from '../keywords/open-keyword-articles';
import { goBackOrHome } from '../navigation/go-back-or-home';
import { ArticleDetailBrief } from './article-detail-brief';
import { toOriginalButtonLabel, toShareMessage } from './article-detail-view';
import { useArticleDetail } from './use-article-detail';

const BACK_ACCESSIBILITY_LABEL = '뒤로';

/**
 * 기사 상세 화면 (탭 밖, 앱 안 브리프): 그림 > 배지·CVSS > 제목 > 출처·게시일 > 브리프(AI 브리핑·확인된 정보·함께 볼 소식)
 * 아래 고정 줄에 원문 보기와 공유. 조회가 실패해도 넘겨받은 기본 정보와 원문 보기는 남음
 */
export function ArticleDetailScreen() {
  const { articleId } = useLocalSearchParams<{ articleId: string }>();
  const { detailQuery, article } = useArticleDetail(articleId ?? '');
  const markArticleRead = useMarkArticleRead();

  // 화면을 연 것으로 읽음 처리 (기기에만 저장)
  useEffect(() => {
    if (articleId) markArticleRead(articleId);
  }, [articleId, markArticleRead]);

  return (
    <SbScreen>
      <SbScreenHeader title={article?.kindLabel} backAccessibilityLabel={BACK_ACCESSIBILITY_LABEL} onBackPress={goBackOrHome} />
      <ScrollView contentContainerStyle={styles.content}>
        {article !== undefined && <ArticleDetailHeading article={article} />}
        {renderBrief()}
      </ScrollView>
      {article !== undefined && (
        <SbBottomActionBar
          primaryAction={
            <SbFilledButton
              label={toOriginalButtonLabel(article)}
              trailingIconName="open-in-new"
              onPress={() => openOriginalArticle(article.originalUrl)}
            />
          }
          secondaryAction={
            <SbIconButton
              iconName="ios-share"
              accessibilityLabel="공유하기"
              onPress={() => void Share.share({ message: toShareMessage(article) }).catch(() => undefined)}
            />
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
    return <ArticleDetailBrief detail={detailQuery.data} />;
  }
}

/** 상세 머리: 그림(+CVSS 게이지) > 배지(종류는 화면 제목에 있어 생략) > 제목 > (원문 제목) > 출처·게시일 > 해시태그 */
function ArticleDetailHeading({ article }: { article: ArticleRowView }) {
  const { colors } = useSbTheme();
  return (
    <View>
      <View style={styles.cover}>
        <SbArticleImage imageUrl={article.cover.imageUrl} coverKind={article.cover.coverKind} coverTone={article.cover.coverTone} />
        {article.cvss !== undefined && (
          <View style={styles.gauge}>
            <SbCvssGauge value={article.cvss} isBoxed />
          </View>
        )}
      </View>
      <View style={styles.heading}>
        <SbBadgeRow badges={toBadgesWithoutImplied(article.badges, article.kindLabel)} />
        <View accessibilityRole="header">
          <SbText variant="detailTitle" color={colors.label}>
            {article.title}
          </SbText>
        </View>
        {article.originalTitle !== undefined && (
          <SbText variant="caption" color={colors.tertiaryLabel}>
            {article.originalTitle}
          </SbText>
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
  heading: { gap: sbSpacing.small, paddingHorizontal: sbSpacing.pageHorizontal, paddingVertical: sbSpacing.medium },
});
