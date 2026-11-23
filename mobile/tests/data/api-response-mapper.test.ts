/**
 * 응답 변환: 서버 enum 이름 > 앱 값, null > undefined, 날짜 > Date
 */
import {
  type ArticleApiResponse,
  type SourceStatusApiResponse,
  toApiTopic,
  toArticlePage,
  toBriefingItem,
  toCollectSourceStatus,
  toTodayBriefing,
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
