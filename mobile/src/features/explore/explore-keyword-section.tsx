import { View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import { SbEntrance, SbInlineErrorState, SbRatioRow, SbSectionHeader } from '@/design';

import { toKeywordRankViews } from '../keywords/keyword-view-state';
import { openKeywordArticles } from '../keywords/open-keyword-articles';
import { useTrendingKeywords } from '../keywords/use-trending-keywords';

const SECTION_TITLE = '이번 주 키워드 순위';
// 순위에 보일 키워드 수
const RANKED_KEYWORD_COUNT = 10;

/** 탐색 탭 키워드 순위: 순위 + #키워드 + 순위 변화(▲▼·NEW) + 건수 막대. 조회 실패는 오류·다시 시도, 키워드가 없으면 구역을 그리지 않음 */
export function ExploreKeywordSection() {
  const trendingKeywordsQuery = useTrendingKeywords();
  if (trendingKeywordsQuery.isError && trendingKeywordsQuery.data === undefined) {
    const failure = toAppFailure(trendingKeywordsQuery.error);
    return (
      <View>
        <SbSectionHeader title={SECTION_TITLE} leadingIconName="auto-awesome" />
        <SbInlineErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => trendingKeywordsQuery.refetch()} />
      </View>
    );
  }
  const keywordRankViews = toKeywordRankViews(trendingKeywordsQuery.data ?? [], RANKED_KEYWORD_COUNT);
  if (keywordRankViews.length === 0) return null;
  return (
    <View>
      <SbSectionHeader title={SECTION_TITLE} leadingIconName="auto-awesome" caption="최근 7일 소식 제목에서 AI 가 뽑은 키워드예요." />
      {keywordRankViews.map((keywordRankView, keywordIndex) => (
        <SbEntrance key={keywordRankView.keyword} order={keywordIndex}>
          <SbRatioRow
            leadingLabel={keywordRankView.rankLabel}
            title={keywordRankView.title}
            valueLabel={keywordRankView.countLabel}
            change={keywordRankView.change}
            ratio={keywordRankView.ratio}
            order={keywordIndex}
            accessibilityLabel={keywordRankView.accessibilityLabel}
            onPress={() => openKeywordArticles(keywordRankView.keyword)}
          />
        </SbEntrance>
      ))}
    </View>
  );
}
