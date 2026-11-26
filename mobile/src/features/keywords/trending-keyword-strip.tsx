import { StyleSheet, View } from 'react-native';

import { SbHashtagChips, SbSectionHeader, sbSpacing } from '@/design';

import { toHashtagAccessibilityLabel, toTrendingHashtags } from './keyword-view-state';
import { openKeywordArticles } from './open-keyword-articles';
import { useTrendingKeywords } from './use-trending-keywords';

// 홈에 보일 키워드 칩 수
const STRIP_KEYWORD_COUNT = 8;

/** 홈의 키워드 바로가기: 최근 많이 나온 키워드 칩 (조회가 안 되거나 키워드가 없으면 구역 자체를 그리지 않음) */
export function TrendingKeywordStrip() {
  const trendingKeywordsQuery = useTrendingKeywords();
  const hashtags = toTrendingHashtags(trendingKeywordsQuery.data ?? []).slice(0, STRIP_KEYWORD_COUNT);
  if (hashtags.length === 0) return null;
  return (
    <View>
      <SbSectionHeader title="요즘 키워드" leadingIconName="auto-awesome" caption="최근 7일 소식 제목에서 AI 가 뽑았어요." />
      <View style={styles.chips}>
        <SbHashtagChips
          hashtags={hashtags}
          size="large"
          toAccessibilityLabel={toHashtagAccessibilityLabel}
          onHashtagPress={openKeywordArticles}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  chips: { paddingHorizontal: sbSpacing.pageHorizontal },
});
