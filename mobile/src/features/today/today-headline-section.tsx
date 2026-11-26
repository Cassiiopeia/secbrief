import { router } from 'expo-router';
import { View } from 'react-native';

import { SbHorizontalRail, SbSectionHeader, sbSize } from '@/design';

import { ArticleFeedItem } from '../articles/article-feed-item';
import { toNewsHref } from '../news/news-feed-filter';
import type { TodayHeadlineView } from './today-view-state';

// 전체 보기 > 소식 탭의 주요만
const onSeeAllPressed = () => router.navigate(toNewsHref('important'));

/** 오늘 탭 주요 소식 구역: 구역 제목(AI 표시, 전체 보기) > 큰 카드 가로 레일 (한 건이면 화면 너비 카드) */
export function TodayHeadlineSection({ headline }: { headline: TodayHeadlineView }) {
  const hasSingleCard = headline.cards.length === 1;
  return (
    <View>
      <SbSectionHeader
        title={headline.title}
        leadingIconName={headline.isAiPicked ? 'auto-awesome' : undefined}
        caption={headline.caption}
        action={{ label: '전체 보기', accessibilityLabel: '주요 소식 전체 보기', onPress: onSeeAllPressed }}
      />
      {hasSingleCard ? (
        <ArticleFeedItem rowView={headline.cards[0]} presentation="headline" />
      ) : (
        <SbHorizontalRail snapItemWidth={sbSize.railCardWidth}>
          {headline.cards.map((card) => (
            <ArticleFeedItem key={card.id} rowView={card} presentation="headline" headlineLayout="rail" />
          ))}
        </SbHorizontalRail>
      )}
    </View>
  );
}
