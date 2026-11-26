/**
 * 응답 변환: 서버 enum 이름 > 앱 값, null > undefined, 날짜 > Date
 */
import {
  type ArticleApiResponse,
  type SourceStatusApiResponse,
  toApiImportance,
  toApiTopic,
  toArticleDetail,
  toArticlePage,
  toArticleSearchResult,
  toBriefingItem,
  toCollectSourceStatus,
  toTodayBriefing,
  toTrendingKeywords,
} from '@/data/mappers/api-response-mapper';

const sampleArticleResponse: ArticleApiResponse = {
  id: 7,
  kind: 'VULNERABILITY',
  region: 'OVERSEAS',
  topic: 'VULNERABILITY',
  title: 'CVE-2026-1111: Example remote code execution',
  translatedTitle: null,
  originalUrl: 'https://nvd.nist.gov/vuln/detail/CVE-2026-1111',
  publisherName: 'NVD',
  publishedAt: '2026-11-23T00:00:00Z',
  isPublishedAtEstimated: false,
  cveId: 'CVE-2026-1111',
  cvssScore: 9.8,
  cvssVersion: '3.1',
  isKev: true,
  isUrgent: true,
};

const sampleSourceResponse: SourceStatusApiResponse = {
  code: 'KISA',
  displayName: 'KISA 보호나라',
  kind: 'ADVISORY',
  region: 'DOMESTIC',
  lastCollectedAt: '2026-11-23T00:00:00Z',
  lastStatus: 'SUCCESS',
};

test('mapsUnknownTopicToGeneral', () => {
  expect(toBriefingItem({ ...sampleArticleResponse, topic: 'MALWARE' }).topic).toBe('general');
});
test('새 주제 SUPPLY_CHAIN·POLICY 를 받는다', () => {
  expect(toBriefingItem({ ...sampleArticleResponse, topic: 'SUPPLY_CHAIN' }).topic).toBe('supplyChain');
  expect(toBriefingItem({ ...sampleArticleResponse, topic: 'POLICY' }).topic).toBe('policy');
});
test('원문 제목과 번역 제목을 따로 둔다', () => {
  const item = toBriefingItem({ ...sampleArticleResponse, title: 'Critical flaw in Next.js', translatedTitle: 'Next.js 심각 취약점' });
  expect(item.title).toBe('Critical flaw in Next.js');
  expect(item.translatedTitle).toBe('Next.js 심각 취약점');
  expect(toBriefingItem({ ...sampleArticleResponse, translatedTitle: null }).translatedTitle).toBeUndefined();
  expect(toBriefingItem({ ...sampleArticleResponse, translatedTitle: '' }).translatedTitle).toBeUndefined();
});
test('REMOTE_ACCESS 는 remoteAccess, 날짜는 Date', () => {
  const item = toBriefingItem({ ...sampleArticleResponse, topic: 'REMOTE_ACCESS', publishedAt: '2026-11-23T00:00:00Z' });
  expect(item.topic).toBe('remoteAccess');
  expect(item.publishedAt.toISOString()).toBe('2026-11-23T00:00:00.000Z');
});
test('null cvss·cve 는 undefined', () => {
  const item = toBriefingItem({ ...sampleArticleResponse, cvssScore: null, cveId: null });
  expect(item.cvssScore).toBeUndefined();
  expect(item.cveId).toBeUndefined();
});
test('모르는 종류는 news, 모르는 지역은 overseas', () => {
  const item = toBriefingItem({ ...sampleArticleResponse, kind: 'BLOG', region: 'MARS' });
  expect(item.kind).toBe('news');
  expect(item.region).toBe('overseas');
});
test('nextCursor null 은 undefined', () => {
  expect(toArticlePage({ items: [], nextCursor: null }).nextCursor).toBeUndefined();
});
test('lastStatus SUCCESS 만 성공', () => {
  expect(toCollectSourceStatus({ ...sampleSourceResponse, lastStatus: 'FAILED' }).isLastCollectSucceeded).toBe(false);
  expect(toCollectSourceStatus(sampleSourceResponse).isLastCollectSucceeded).toBe(true);
  expect(toCollectSourceStatus({ ...sampleSourceResponse, lastCollectedAt: null }).lastCollectedAt).toBeUndefined();
});
test('toApiTopic 은 서버 enum 이름', () => {
  expect(toApiTopic('supplyChain')).toBe('SUPPLY_CHAIN');
});
test('오늘 응답: 서버 날짜는 그날 00:00, 긴급·동향 null 은 undefined', () => {
  const briefing = toTodayBriefing({
    date: '2026-11-23', urgentItem: null, todayCount: 3, weeklyCount: 9, topTrend: null, streamItems: [sampleArticleResponse],
  });
  expect(briefing.date.getDate()).toBe(23);
  expect(briefing.urgentItem).toBeUndefined();
  expect(briefing.topTrend).toBeUndefined();
  expect(briefing.streamItems).toHaveLength(1);
});
test('imagePath: 값이 있으면 그대로, null·빈 값·필드 없음은 undefined', () => {
  expect(toBriefingItem({ ...sampleArticleResponse, imagePath: '/api/v1/articles/7/image' }).imagePath).toBe('/api/v1/articles/7/image');
  expect(toBriefingItem({ ...sampleArticleResponse, imagePath: null }).imagePath).toBeUndefined();
  expect(toBriefingItem({ ...sampleArticleResponse, imagePath: '' }).imagePath).toBeUndefined();
  // 이 필드가 생기기 전 서버 응답 (필드 자체가 없음)
  expect(toBriefingItem(sampleArticleResponse).imagePath).toBeUndefined();
});
test('importance: 서버 이름 > 앱 값, 없거나 모르는 값은 routine', () => {
  expect(toBriefingItem({ ...sampleArticleResponse, importance: 'HEADLINE' }).importance).toBe('headline');
  expect(toBriefingItem({ ...sampleArticleResponse, importance: 'NOTABLE' }).importance).toBe('notable');
  expect(toBriefingItem({ ...sampleArticleResponse, importance: 'BREAKING' }).importance).toBe('routine');
  expect(toBriefingItem({ ...sampleArticleResponse, importance: null }).importance).toBe('routine');
  expect(toBriefingItem(sampleArticleResponse).importance).toBe('routine');
});
test('subjectName·sourceCode: 값이 있으면 그대로, null·필드 없음은 undefined', () => {
  const item = toBriefingItem({ ...sampleArticleResponse, subjectName: 'VMware', sourceCode: 'CISA_KEV' });
  expect(item.subjectName).toBe('VMware');
  expect(item.sourceCode).toBe('CISA_KEV');
  expect(toBriefingItem({ ...sampleArticleResponse, subjectName: null, sourceCode: null }).subjectName).toBeUndefined();
  expect(toBriefingItem(sampleArticleResponse).sourceCode).toBeUndefined();
});
test('오늘 응답의 headlineItems: 없거나 null 이면 빈 배열', () => {
  const baseResponse = { date: '2026-11-23', urgentItem: null, todayCount: 0, weeklyCount: 0, topTrend: null, streamItems: [] };
  expect(toTodayBriefing(baseResponse).headlineItems).toEqual([]);
  expect(toTodayBriefing({ ...baseResponse, headlineItems: null }).headlineItems).toEqual([]);
  expect(toTodayBriefing({ ...baseResponse, headlineItems: [sampleArticleResponse] }).headlineItems).toHaveLength(1);
});
test('keywords: 없으면 빈 배열, 행사 주제 EVENT, 중요도 서버 이름', () => {
  expect(toBriefingItem(sampleArticleResponse).keywords).toEqual([]);
  expect(toBriefingItem({ ...sampleArticleResponse, keywords: ['VPN', '제로데이'] }).keywords).toEqual(['VPN', '제로데이']);
  expect(toBriefingItem({ ...sampleArticleResponse, topic: 'EVENT' }).topic).toBe('event');
  expect(toApiTopic('event')).toBe('EVENT');
  expect(toApiImportance('notable')).toBe('NOTABLE');
});
test('키워드 순위: rankChange null 은 신규, 필드가 없으면 변화 모름·순위는 받은 순서', () => {
  const keywords = toTrendingKeywords({
    keywords: [
      { keyword: '랜섬웨어', articleCount: 12, previousArticleCount: 5, rank: 1, rankChange: 2 },
      { keyword: '제로데이', articleCount: 3, previousArticleCount: 0, rank: 2, rankChange: null },
      { keyword: 'VPN', articleCount: 2 },
    ],
  });
  expect(keywords[0]).toEqual({ keyword: '랜섬웨어', articleCount: 12, rank: 1, isNew: false, rankChange: 2 });
  expect(keywords[1]).toMatchObject({ isNew: true, rankChange: undefined });
  expect(keywords[2]).toEqual({ keyword: 'VPN', articleCount: 2, rank: 3, isNew: false, rankChange: undefined });
});
test('검색 응답: SEMANTIC 만 의미 검색, 그 밖은 글자 일치 검색', () => {
  expect(toArticleSearchResult({ items: [sampleArticleResponse], searchMode: 'SEMANTIC' }).searchMode).toBe('semantic');
  expect(toArticleSearchResult({ items: [], searchMode: 'KEYWORD' }).searchMode).toBe('keyword');
  expect(toArticleSearchResult({ items: [], searchMode: 'OTHER' }).searchMode).toBe('keyword');
});
test('기사 상세: 요약이 없으면 undefined·빈 배열, 값은 받은 그대로', () => {
  const pendingDetail = toArticleDetail({ article: sampleArticleResponse, summary: null, keyPoints: [], actionGuide: null, isAiSummarized: false, facts: [], relatedItems: [] });
  expect(pendingDetail).toMatchObject({ summary: undefined, keyPoints: [], actionGuide: undefined, isAiSummarized: false, facts: [], relatedItems: [] });
  expect(toArticleDetail({ article: sampleArticleResponse })).toMatchObject({ keyPoints: [], facts: [], relatedItems: [] });
  const detail = toArticleDetail({
    article: sampleArticleResponse,
    summary: '요약',
    keyPoints: ['핵심'],
    actionGuide: '대응',
    isAiSummarized: true,
    facts: [{ label: '조치 버전', value: '원문에 없음' }],
    relatedItems: [sampleArticleResponse],
  });
  expect(detail.facts).toEqual([{ label: '조치 버전', value: '원문에 없음' }]);
  expect(detail.relatedItems[0].id).toBe('7');
  expect(detail.isAiSummarized).toBe(true);
});
