import type { ArticleKind } from '@/data/models/briefing-item';
import { type SbArticleHashtags, SbArticleRow, SbHeadlineCard } from '@/design';

import { toHashtagAccessibilityLabel } from '../keywords/keyword-view-state';
import { openKeywordArticles } from '../keywords/open-keyword-articles';
import { toFeedBadges } from './article-badges';
import type { ArticleFeedPresentation } from './article-feed-layout';
import { toKindLabel } from './article-labels';
import type { ArticleRowView } from './article-row-view';
import { useArticleOpener } from './use-article-opener';
import { useReadArticleIds } from './use-read-articles';

interface ArticleFeedItemProps {
  rowView: ArticleRowView;
  /** headline 큰 카드, card 중간 카드, compact 조밀한 행 */
  presentation: ArticleFeedPresentation;
  /** 큰 카드 배치 (기본 wide: 화면 너비, rail: 가로 레일 안) */
  headlineLayout?: 'rail' | 'wide';
  /** 목록 자리만 봐도 아는 종류 (그 종류 배지는 생략. 예: 취약점 탭의 취약점) */
  impliedKind?: ArticleKind;
}

/**
 * 소식 한 건 표현 (모든 목록·레일 공통): 표현 크기에 맞는 디자인 컴포넌트 고르기, 누르면 앱 안 상세, 해시태그 누르면 키워드 목록
 * 배지는 2개까지 (조밀한 행은 분류 배지 없이), 해시태그는 카드에만. 읽은 소식은 제목을 흐리게. 제목 아래 줄은 한 줄 결론 우선
 * 그림은 실제 대표 이미지만 (없으면 글만, 제목 전체)
 */
export function ArticleFeedItem({ rowView, presentation, headlineLayout = 'wide', impliedKind }: ArticleFeedItemProps) {
  const openArticle = useArticleOpener();
  const isRead = useReadArticleIds().has(rowView.id);
  const onPress = () => openArticle(rowView);
  const isCompact = presentation === 'compact';
  const badges = toFeedBadges(rowView.badges, isCompact, impliedKind && toKindLabel(impliedKind));
  const hashtags: SbArticleHashtags | undefined =
    rowView.hashtags.length === 0
      ? undefined
      : { hashtags: rowView.hashtags, toAccessibilityLabel: toHashtagAccessibilityLabel, onHashtagPress: openKeywordArticles };

  if (presentation === 'headline') {
    return (
      <SbHeadlineCard
        layout={headlineLayout}
        title={rowView.title}
        secondaryLine={rowView.secondaryLine}
        metaLabel={rowView.compactMetaLabel}
        translatedMarkLabel={rowView.translatedMarkLabel}
        badges={badges}
        imageUrl={rowView.imageUrl}
        cvss={rowView.cvss}
        hashtags={hashtags}
        isRead={isRead}
        accessibilityLabel={rowView.accessibilityLabel}
        onPress={onPress}
      />
    );
  }
  return (
    <SbArticleRow
      density={isCompact ? 'compact' : 'card'}
      title={rowView.title}
      secondaryLine={rowView.secondaryLine}
      metaLabel={rowView.compactMetaLabel}
      translatedMarkLabel={rowView.translatedMarkLabel}
      badges={badges}
      cvss={rowView.cvss}
      imageUrl={rowView.imageUrl}
      hashtags={hashtags}
      isRead={isRead}
      accessibilityLabel={rowView.accessibilityLabel}
      onPress={onPress}
    />
  );
}
