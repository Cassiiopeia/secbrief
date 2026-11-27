/**
 * 탭 필터 변환: 소식 탭 카테고리·주제 칩(경로 파라미터), 취약점 탭 세그먼트·출처 묶음, 검색어, 키워드 순위, 기사 상세
 */
import type { ArticleDetail } from '@/data/models/article-detail';
import type { TrendingKeyword } from '@/data/models/trending-keyword';
import { sbMaximumFontScale, toSbEffectiveFontScale } from '@/design/tokens/sb-typography';
import {
  toArticleDetailView,
  toMaximumPublisherNameWidthUnits,
  toOriginalButtonAccessibilityLabel,
  toOriginalButtonLabel,
} from '@/features/article-detail/article-detail-view';
import { toKeywordRankViews, toTrendingHashtags } from '@/features/keywords/keyword-view-state';
import { hasTopicChips, toNewsFeedQuery, toNewsHref, toNewsSelection } from '@/features/news/news-feed-filter';
import { isSearchQueryTooShort, toSearchModeNotice, toSearchQuery } from '@/features/search/search-query';
import {
  DEFAULT_VULNERABILITY_FEED_SELECTION,
  toSelectionWithSegment,
  toSelectionWithSourceCode,
  toSelectionWithSourceGroup,
  toVulnerabilityFeedQuery,
} from '@/features/vulnerability/vulnerability-feed-selection';

import { NOW, kisaItem, nvdItem } from './briefing-item-fixtures';

describe('소식 탭', () => {
  test('주요만은 뉴스 + 주요 보기, 국내·해외는 지역, 주제 칩은 주제', () => {
    expect(toNewsFeedQuery('important', 'all')).toEqual({ baseFilter: { kind: 'news' }, importanceView: 'important' });
    expect(toNewsFeedQuery('domestic', 'ransomware')).toEqual({
      baseFilter: { kind: 'news', region: 'domestic', topic: 'ransomware' },
      importanceView: 'all',
    });
  });
  test('행사는 종류를 가리지 않고 행사 주제 전부, 주제 칩 없음', () => {
    expect(toNewsFeedQuery('event', 'ransomware')).toEqual({ baseFilter: { topic: 'event' }, importanceView: 'all' });
    expect(hasTopicChips('event')).toBe(false);
    expect(hasTopicChips('all')).toBe(true);
  });
  test('경로 파라미터 > 고른 칩: 없거나 모르는 값이면 주요만 · 모든 주제', () => {
    expect(toNewsSelection({})).toEqual({ category: 'important', topicFilter: 'all' });
    expect(toNewsSelection({ category: 'all', topic: 'ransomware' })).toEqual({ category: 'all', topicFilter: 'ransomware' });
    expect(toNewsSelection({ category: 'unknown', topic: 'event' })).toEqual({ category: 'important', topicFilter: 'all' });
  });
  test('홈의 전체 보기 경로: 최신 소식은 전체, 주요 소식은 주요만 (주제는 모든 주제로)', () => {
    expect(toNewsHref('all')).toEqual({ pathname: '/news', params: { category: 'all', topic: 'all' } });
    expect(toNewsHref('important')).toEqual({ pathname: '/news', params: { category: 'important', topic: 'all' } });
  });
});

describe('취약점 탭', () => {
  const noInterests = { productCodes: [], fieldCodes: [], ecosystemCodes: [] };
  const todayStart = new Date('2026-11-26T15:00:00Z');

  test('기본은 모든 출처의 주요만', () => {
    expect(toVulnerabilityFeedQuery(DEFAULT_VULNERABILITY_FEED_SELECTION, noInterests, todayStart)).toEqual({
      baseFilter: { kind: 'vulnerability' },
      importanceView: 'important',
      layout: 'feed',
    });
  });
  test('악용 중은 CISA KEV 출처, 출처 묶음을 전체로 되돌린다', () => {
    const kisaSelection = toSelectionWithSourceGroup(DEFAULT_VULNERABILITY_FEED_SELECTION, 'kisa');
    const exploitedSelection = toSelectionWithSegment(kisaSelection, 'exploited');
    expect(exploitedSelection).toEqual({ segment: 'exploited', sourceGroup: 'all', isTodayOnly: false });
    expect(toVulnerabilityFeedQuery(exploitedSelection, noInterests, todayStart)).toEqual({
      baseFilter: { kind: 'vulnerability', sourceCode: 'CISA_KEV' },
      importanceView: 'all',
      layout: 'feed',
    });
  });
  test('출처 묶음을 고르면 첫 출처, 악용 중이었다면 주요만으로', () => {
    const selection = toSelectionWithSourceGroup({ segment: 'exploited', sourceGroup: 'all', isTodayOnly: false }, 'kisa');
    expect(selection).toEqual({ segment: 'important', sourceGroup: 'kisa', sourceCode: 'KISA', isTodayOnly: false });
    const reportSelection = toSelectionWithSourceCode(toSelectionWithSegment(selection, 'all'), 'KISA_REPORT');
    expect(toVulnerabilityFeedQuery(reportSelection, noInterests, todayStart)).toEqual({
      baseFilter: { sourceCode: 'KISA_REPORT' },
      importanceView: 'all',
      layout: 'feed',
    });
    expect(toSelectionWithSourceGroup(reportSelection, 'all')).toEqual({ segment: 'all', sourceGroup: 'all', isTodayOnly: false });
  });
  test('오픈소스는 GitHub 권고 2곳 + 생태계, 주요 카드·한 줄 행 배치 (세그먼트와 상관없이 나눠 부름)', () => {
    const selection = { ...toSelectionWithSourceGroup(DEFAULT_VULNERABILITY_FEED_SELECTION, 'openSource'), ecosystemCode: 'eco-a' };
    expect(selection.sourceCode).toBeUndefined();
    expect(toVulnerabilityFeedQuery(selection, noInterests, todayStart)).toEqual({
      baseFilter: { sourceCodes: ['GHSA', 'GHSA_HIGH'], ecosystemCode: 'eco-a' },
      importanceView: 'all',
      layout: 'openSourceSplit',
    });
  });
  test('내 기술은 관심 기술 3가지, 오늘만이면 오늘 0시 이후 수집분', () => {
    const interests = { productCodes: ['product-a'], fieldCodes: ['field-a'], ecosystemCodes: [] };
    const selection = { ...toSelectionWithSourceGroup(DEFAULT_VULNERABILITY_FEED_SELECTION, 'myTech'), isTodayOnly: true };
    expect(toVulnerabilityFeedQuery(selection, interests, todayStart).baseFilter).toEqual({ interestCodes: interests, collectedSince: todayStart });
  });
});

describe('검색', () => {
  test('검색어: 앞뒤 공백 제거, 2자 미만은 검색하지 않음, 100자까지', () => {
    expect(toSearchQuery('  VPN  ')).toBe('VPN');
    expect(toSearchQuery(' a ')).toBeUndefined();
    expect(toSearchQuery('')).toBeUndefined();
    expect(toSearchQuery('가'.repeat(120))).toHaveLength(100);
  });
  test('한 글자만 입력하면 짧다고 안내 (빈 입력은 안내 없음)', () => {
    expect(isSearchQueryTooShort('a')).toBe(true);
    expect(isSearchQueryTooShort(' 가 ')).toBe(true);
    expect(isSearchQueryTooShort('')).toBe(false);
    expect(isSearchQueryTooShort('   ')).toBe(false);
    expect(isSearchQueryTooShort('VPN')).toBe(false);
  });
  test('검색 방식 안내', () => {
    expect(toSearchModeNotice('semantic')).toBe('AI 가 뜻이 비슷한 소식을 찾았어요.');
    expect(toSearchModeNotice('keyword')).toBe('글자가 들어간 소식을 찾았어요.');
  });
});

describe('키워드', () => {
  const trendingKeywords: TrendingKeyword[] = [
    { keyword: '랜섬웨어', articleCount: 12, rank: 1, isNew: false, rankChange: 2 },
    { keyword: 'VPN', articleCount: 6, rank: 2, isNew: false, rankChange: -1 },
    { keyword: '제로데이', articleCount: 3, rank: 3, isNew: true },
    { keyword: '패치', articleCount: 3, rank: 4, isNew: false, rankChange: 0 },
    { keyword: '공급망', articleCount: 2, rank: 5, isNew: false },
    { keyword: ' ', articleCount: 9, rank: 6, isNew: false },
    { keyword: '없음', articleCount: 0, rank: 7, isNew: false },
  ];
  test('순위 줄: 1위 대비 막대, 순위 변화(상승·하락·신규·유지·모름)', () => {
    const rankViews = toKeywordRankViews(trendingKeywords, 10);
    expect(rankViews.map((view) => view.title)).toEqual(['#랜섬웨어', '#VPN', '#제로데이', '#패치', '#공급망']);
    expect(rankViews.map((view) => view.ratio)).toEqual([1, 0.5, 0.25, 0.25, 2 / 12]);
    expect(rankViews.map((view) => view.change?.direction)).toEqual(['up', 'down', 'new', 'flat', undefined]);
    expect(rankViews[0].change?.label).toBe('2');
    expect(rankViews[2].change?.label).toBe('NEW');
    expect(toKeywordRankViews(trendingKeywords, 2)).toHaveLength(2);
  });
  test('칩 구름: 건수 표시, 빈 키워드·0건 제외', () => {
    expect(toTrendingHashtags(trendingKeywords)[0]).toEqual({ keyword: '랜섬웨어', countLabel: '12건' });
    expect(toTrendingHashtags(trendingKeywords)).toHaveLength(5);
  });
});

describe('기사 상세', () => {
  const detail: ArticleDetail = {
    article: nvdItem,
    summary: '요약 문장이에요.',
    keyPoints: ['핵심 하나', ' ', '핵심 둘'],
    actionGuide: undefined,
    isAiSummarized: true,
    isSummaryPending: false,
    facts: [{ label: '영향 버전', value: '원문에 없음' }, { label: ' ', value: '값' }],
    relatedItems: [nvdItem, kisaItem],
    responseGuideSections: [],
  };
  test('요약·핵심·확인된 정보는 받은 값 그대로, AI 가 썼으면 표시, 자기 자신은 함께 볼 소식에서 뺌', () => {
    const view = toArticleDetailView(detail, NOW);
    expect(view.summary).toBe('요약 문장이에요.');
    expect(view.keyPoints).toEqual(['핵심 하나', '핵심 둘']);
    expect(view.aiNoticeLabel).toBe('AI 가 요약했어요');
    expect(view.facts).toEqual([{ label: '영향 버전', value: '원문에 없음' }]);
    expect(view.relatedArticles.map((article) => article.id)).toEqual([kisaItem.id]);
    expect(view.isSummaryMissing).toBe(false);
    expect(view.moreArticlesLink).toBeUndefined();
  });
  test('함께 볼 소식이 없으면 다른 소식 이동: 같은 주제 모아 보기, 주제가 일반이면 홈', () => {
    expect(toArticleDetailView({ ...detail, relatedItems: [nvdItem] }, NOW).moreArticlesLink).toEqual({
      label: '다른 원격 접속 소식 보러 가기',
      browseKey: 'topic-remoteAccess',
    });
    expect(toArticleDetailView({ ...detail, article: { ...nvdItem, topic: 'general' }, relatedItems: [] }, NOW).moreArticlesLink).toEqual({
      label: '다른 소식 보러 가기',
    });
  });
  test('요약이 없으면 요약 없음 안내, AI 표시 없음', () => {
    const view = toArticleDetailView({ ...detail, summary: undefined, keyPoints: [], isAiSummarized: false }, NOW);
    expect(view.isSummaryMissing).toBe(true);
    expect(view.aiNoticeLabel).toBeUndefined();
  });
  test('원문 보기 버튼에는 게시자 이름', () => {
    const { article } = toArticleDetailView(detail, NOW);
    expect(toOriginalButtonLabel(article)).toBe('NVD 원문 보기');
    expect(toOriginalButtonAccessibilityLabel(article)).toBe('NVD 원문 보기');
  });
  test('게시자 이름이 360pt 폭 버튼에 안 들어가면 "원문 보기"만, 읽기 도우미 문구는 게시자 이름까지 (#112)', () => {
    const { article } = toArticleDetailView(detail, NOW);
    // 13 단위까지 그대로: 반각 13자, 한글 섞인 "KISA 경보단계"(5 + 4x2)
    for (const fittingPublisherName of ['데일리시큐', 'CISA KEV', 'KISA 보호나라', 'KISA 경보단계', 'A'.repeat(13)]) {
      const fittingArticle = { ...article, publisherName: fittingPublisherName };
      expect(toOriginalButtonLabel(fittingArticle)).toBe(`${fittingPublisherName} 원문 보기`);
    }
    // 14 단위부터 줄임: 반각 14자, 한글 7자는 14 단위
    for (const longPublisherName of ['BleepingComputer', 'The Hacker News', 'GitHub Advisory', 'Red Hat 보안 권고', 'A'.repeat(14), '가'.repeat(7)]) {
      const longArticle = { ...article, publisherName: longPublisherName };
      expect(toOriginalButtonLabel(longArticle)).toBe('원문 보기');
      expect(toOriginalButtonAccessibilityLabel(longArticle)).toBe(`${longPublisherName} 원문 보기`);
    }
  });
  test('글자 배율만큼 이름 칸이 줄어요: 1배 13, 1.3배 8, 2배(버튼 한도 1.6배) 5 단위 (#119)', () => {
    const buttonFontScaleAt2 = toSbEffectiveFontScale(2, 'button');
    expect(buttonFontScaleAt2).toBe(sbMaximumFontScale.text);
    expect(toMaximumPublisherNameWidthUnits(1)).toBe(13);
    expect(toMaximumPublisherNameWidthUnits(toSbEffectiveFontScale(1.3, 'button'))).toBe(8);
    expect(toMaximumPublisherNameWidthUnits(buttonFontScaleAt2)).toBe(5);
  });
  test('큰 글자에서 "KISA 보호나라" 는 "원문 보기", 읽기 도우미 문구는 그대로 (#119)', () => {
    const { article } = toArticleDetailView(detail, NOW);
    const kisaArticle = { ...article, publisherName: 'KISA 보호나라' };
    expect(toOriginalButtonLabel(kisaArticle, 1)).toBe('KISA 보호나라 원문 보기');
    expect(toOriginalButtonLabel(kisaArticle, toSbEffectiveFontScale(1.3, 'button'))).toBe('원문 보기');
    expect(toOriginalButtonLabel(kisaArticle, toSbEffectiveFontScale(2, 'button'))).toBe('원문 보기');
    expect(toOriginalButtonAccessibilityLabel(kisaArticle)).toBe('KISA 보호나라 원문 보기');
    // 짧은 이름은 1.3배까지 그대로 (8 단위 "CISA KEV"), 2배에서는 5 단위 "NVD" 만
    const cisaArticle = { ...article, publisherName: 'CISA KEV' };
    expect(toOriginalButtonLabel(cisaArticle, toSbEffectiveFontScale(1.3, 'button'))).toBe('CISA KEV 원문 보기');
    expect(toOriginalButtonLabel(cisaArticle, toSbEffectiveFontScale(2, 'button'))).toBe('원문 보기');
    expect(toOriginalButtonLabel(article, toSbEffectiveFontScale(2, 'button'))).toBe('NVD 원문 보기');
  });
});
