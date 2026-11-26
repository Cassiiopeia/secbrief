import { router } from 'expo-router';
import { type LayoutChangeEvent, StyleSheet, View } from 'react-native';

import { SbBriefCard, SbBriefParagraph, SbEntrance, SbFactTable, SbFilledButton, SbNumberedList, SbSectionHeader, sbSpacing } from '@/design';

import { ArticleFeedItem } from '../articles/article-feed-item';
import { toArticleFeedPresentation } from '../articles/article-feed-layout';
import type { ArticleRowView } from '../articles/article-row-view';
import { toBrowseHref } from '../browse/browse-target';
import { goHome } from '../navigation/go-back-or-home';
import { ResponseGuideSections } from '../response-guide/response-guide-sections';
import { ArticleBriefingCard } from './article-briefing-card';
import type { ArticleDetailView, MoreArticlesLink } from './article-detail-view';

// 등장 순번: AI 구역(브리핑 카드 1개 또는 요약·핵심·대응 카드 3개) > 확인된 정보 > 대응 모아보기(또는 함께 볼 소식·다른 소식 버튼)
const BRIEFING_CARD_COUNT = 1;
const SUMMARY_ORDER = 0;
const KEY_POINT_ORDER = 1;
const ACTION_GUIDE_ORDER = 2;
const SUMMARY_CARD_COUNT = 3;
// 요약 카드 대신 쓰는 글 2개: 요약·핵심 모두 없음 (입력 없음·대기·실패 공통, 서버가 상태를 주지 않아 '준비 중'으로 약속하지 않음), 핵심만 있음
const SUMMARY_MISSING_TEXT = '이 소식은 AI 요약 없이 보여 드려요. 내용은 원문 보기에서 확인해 주세요.';
const KEY_POINTS_ONLY_TEXT = '아래 핵심을 확인해 주세요.';

/**
 * 기사 상세의 브리프 구역 (조회가 끝난 뒤): AI 브리핑 카드 > 확인된 정보 > 대응 모아보기
 * 대응 모아보기가 비면 함께 보면 좋은 소식, 그것도 없으면 다른 소식 보러 가기
 * 브리핑이 없는 기사는 이전 방식 카드 3개(요약·핵심·대응)로, 요약도 없으면 준비 중 안내만
 */
export function ArticleDetailBrief({
  detail,
  onResponseGuideLayout,
}: {
  detail: ArticleDetailView;
  /** 대응 모아보기 위치 (브리프 구역 안 기준, 알림 상세에서 들어왔을 때 내려가기용) */
  onResponseGuideLayout?: (event: LayoutChangeEvent) => void;
}) {
  const { briefing } = detail;
  const factOrder = briefing === undefined ? SUMMARY_CARD_COUNT : BRIEFING_CARD_COUNT;
  return (
    <View>
      {briefing === undefined ? (
        <SummaryCards detail={detail} />
      ) : (
        <SbEntrance order={SUMMARY_ORDER}>
          <ArticleBriefingCard briefing={briefing} />
        </SbEntrance>
      )}
      {detail.facts.length > 0 && (
        <SbEntrance order={factOrder}>
          <SbBriefCard title="확인된 정보" iconName="fact-check">
            <SbFactTable facts={detail.facts} expandLabel="더 보기" collapseLabel="접기" />
          </SbBriefCard>
        </SbEntrance>
      )}
      {/* 브리프 구역 바로 아래 자식이라 위치가 브리프 구역 기준 */}
      <View onLayout={detail.responseGuideSections.length > 0 ? onResponseGuideLayout : undefined}>
        <SbEntrance order={factOrder + 1}>{renderRelatedContent()}</SbEntrance>
      </View>
    </View>
  );

  /** 대응 모아보기 > 함께 볼 소식 > 다른 소식 버튼 중 하나 */
  function renderRelatedContent() {
    if (detail.responseGuideSections.length > 0) {
      return <ResponseGuideSections sectionViews={detail.responseGuideSections} />;
    }
    if (detail.moreArticlesLink !== undefined) return <MoreArticlesButton moreArticlesLink={detail.moreArticlesLink} />;
    return <RelatedArticleSection relatedArticles={detail.relatedArticles} />;
  }
}

/**
 * 이전 방식 AI 카드 3개 (브리핑이 없는 기사): 요약 > 핵심 > 이렇게 대응해요
 * 요약이 아직 없으면 준비 중 안내만, 대응은 원문에 있을 때만. AI 가 쓴 카드에는 AI 표시
 */
function SummaryCards({ detail }: { detail: ArticleDetailView }) {
  return (
    <View>
      <SbEntrance order={SUMMARY_ORDER}>
        <SbBriefCard title="요약" iconName="subject" noticeLabel={detail.aiNoticeLabel}>
          <SbBriefParagraph
            text={detail.summary ?? (detail.isSummaryMissing ? SUMMARY_MISSING_TEXT : KEY_POINTS_ONLY_TEXT)}
          />
        </SbBriefCard>
      </SbEntrance>
      {detail.keyPoints.length > 0 && (
        <SbEntrance order={KEY_POINT_ORDER}>
          <SbBriefCard title="핵심" iconName="format-list-numbered" noticeLabel={detail.aiNoticeLabel}>
            <SbNumberedList items={detail.keyPoints} />
          </SbBriefCard>
        </SbEntrance>
      )}
      {detail.actionGuide !== undefined && (
        <SbEntrance order={ACTION_GUIDE_ORDER}>
          <SbBriefCard title="이렇게 대응해요" iconName="verified-user" tone="emphasis" noticeLabel={detail.aiNoticeLabel}>
            <SbBriefParagraph text={detail.actionGuide} />
          </SbBriefCard>
        </SbEntrance>
      )}
    </View>
  );
}

/** 함께 보면 좋은 소식: 중간 카드·조밀한 행 (큰 카드는 쓰지 않음: 본문 아래에서는 가볍게) */
function RelatedArticleSection({ relatedArticles }: { relatedArticles: ArticleRowView[] }) {
  const now = new Date();
  return (
    <View>
      <SbSectionHeader title="함께 보면 좋은 소식" />
      {relatedArticles.map((relatedArticle) => (
        <ArticleFeedItem
          key={relatedArticle.id}
          rowView={relatedArticle}
          presentation={toArticleFeedPresentation(
            {
              importance: relatedArticle.importance === 'routine' ? 'routine' : 'notable',
              publishedAt: relatedArticle.publishedAt,
              listPurpose: 'feed',
              isLead: false,
            },
            now,
          )}
        />
      ))}
    </View>
  );
}

/** 함께 볼 소식이 없을 때의 이동 버튼: 같은 주제 모아 보기, 주제가 일반이면 홈 */
function MoreArticlesButton({ moreArticlesLink }: { moreArticlesLink: MoreArticlesLink }) {
  const { browseKey } = moreArticlesLink;
  return (
    <View style={styles.moreArticles}>
      <SbFilledButton
        label={moreArticlesLink.label}
        trailingIconName="chevron-right"
        tone="quiet"
        onPress={browseKey === undefined ? goHome : () => router.push(toBrowseHref(browseKey))}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  moreArticles: { paddingHorizontal: sbSpacing.tileHorizontal, paddingTop: sbSpacing.small },
});
