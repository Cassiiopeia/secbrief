import { useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { toAppFailure } from '@/core/error/app-failure';
import {
  SbEmptyState,
  SbEntrance,
  SbErrorState,
  SbHashtagChips,
  SbInlineNotice,
  SbListEntrance,
  SbLoadingSkeleton,
  SbScreen,
  SbScreenHeader,
  SbSearchField,
  SbSectionHeader,
  sbSpacing,
} from '@/design';

import { ArticleFeedItem } from '../articles/article-feed-item';
import { toArticleRowView } from '../articles/article-row-view';
import { toHashtagAccessibilityLabel, toTrendingHashtags } from '../keywords/keyword-view-state';
import { openKeywordArticles } from '../keywords/open-keyword-articles';
import { useTrendingKeywords } from '../keywords/use-trending-keywords';
import { goBackOrHome } from '../navigation/go-back-or-home';
import {
  SEARCH_QUERY_MAXIMUM_LENGTH,
  SEARCH_QUERY_TOO_SHORT_NOTICE,
  isSearchQueryTooShort,
  toSearchModeNotice,
  toSearchQuery,
} from './search-query';
import { useArticleSearch } from './use-article-search';
import { useDebouncedValue } from './use-debounced-value';

// 입력을 멈춘 뒤 검색을 시작하기까지 기다리는 시간
const SEARCH_DEBOUNCE_MILLISECONDS = 350;

/**
 * 검색 화면 (탭 밖): 입력창 > (한 글자면 안내) > 입력 전에는 많이 나오는 키워드 칩, 입력 뒤에는 검색 방식 안내 + 결과(조밀한 행)
 * 검색어는 서버로만 보내고 기기에 남기지 않음
 */
export function SearchScreen() {
  const [inputText, setInputText] = useState('');
  const searchQuery = toSearchQuery(useDebouncedValue(inputText, SEARCH_DEBOUNCE_MILLISECONDS));
  const searchResultQuery = useArticleSearch(searchQuery);

  return (
    <SbScreen>
      <SbScreenHeader backAccessibilityLabel="뒤로" onBackPress={goBackOrHome}>
        <SbSearchField
          value={inputText}
          placeholder="제품, 회사, 취약점 번호로 찾아요"
          accessibilityLabel="검색어 입력"
          clearAccessibilityLabel="검색어 지우기"
          maximumLength={SEARCH_QUERY_MAXIMUM_LENGTH}
          onChangeText={setInputText}
        />
      </SbScreenHeader>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        {isSearchQueryTooShort(inputText) && <SbInlineNotice message={SEARCH_QUERY_TOO_SHORT_NOTICE} />}
        {searchQuery === undefined ? <TrendingKeywordSection /> : renderSearchResult()}
      </ScrollView>
    </SbScreen>
  );

  /** 검색 결과: 로딩 뼈대 / 오류(에러 코드) / 빈 결과 / 방식 안내 + 소식들 */
  function renderSearchResult() {
    if (searchResultQuery.isPending) return <SbLoadingSkeleton variant="articleRows" />;
    if (searchResultQuery.isError) {
      const failure = toAppFailure(searchResultQuery.error);
      return (
        <SbErrorState message={failure.userMessage} errorCode={failure.errorCode} onRetry={() => searchResultQuery.refetch()} />
      );
    }
    const now = new Date();
    const rowViews = searchResultQuery.data.items.map((item) => toArticleRowView(item, now));
    if (rowViews.length === 0) {
      return <SbEmptyState illustration="searchEmpty" title="찾는 소식이 없어요" description="다른 낱말로 다시 찾아 주세요." />;
    }
    return (
      <>
        <SbInlineNotice message={toSearchModeNotice(searchResultQuery.data.searchMode)} />
        <View style={styles.results}>
          {rowViews.map((rowView, rowIndex) => (
            <SbListEntrance key={rowView.id} itemIndex={rowIndex}>
              <ArticleFeedItem rowView={rowView} presentation="compact" />
            </SbListEntrance>
          ))}
        </View>
      </>
    );
  }
}

/** 입력 전 화면: 안내 + 많이 나오는 키워드 칩 구름 (키워드 조회가 안 되면 안내만) */
function TrendingKeywordSection() {
  const trendingKeywordsQuery = useTrendingKeywords();
  const trendingHashtags = toTrendingHashtags(trendingKeywordsQuery.data ?? []);
  if (trendingHashtags.length === 0) {
    return <SbEmptyState illustration="searchIdle" title="무엇을 찾을까요?" description="두 글자 이상 입력하면 소식을 찾아 드려요." />;
  }
  return (
    <SbEntrance order={0}>
      <SbSectionHeader title="지금 많이 나오는 키워드" leadingIconName="auto-awesome" caption="최근 7일 소식 제목에서 AI 가 뽑았어요." />
      <View style={styles.keywords}>
        <SbHashtagChips
          hashtags={trendingHashtags}
          size="large"
          toAccessibilityLabel={toHashtagAccessibilityLabel}
          onHashtagPress={openKeywordArticles}
        />
      </View>
    </SbEntrance>
  );
}

const styles = StyleSheet.create({
  // 빈 상태·오류가 남은 높이 가운데에 오도록
  content: { flexGrow: 1, paddingBottom: sbSpacing.large },
  keywords: { paddingHorizontal: sbSpacing.pageHorizontal, paddingTop: sbSpacing.small },
  results: { paddingTop: sbSpacing.compact },
});
