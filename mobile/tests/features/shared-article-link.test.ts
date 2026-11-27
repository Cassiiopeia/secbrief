/**
 * 공유 링크: 공유 주소(/a/{id}), 공유 내용(iOS url 칸 / Android 글에 이어 붙임, 원문 링크 없음), 들어온 링크 > 앱 경로 (예시 값)
 */
import { apiConfig } from '@/core/config/api-config';
import { toSharedArticleUrl } from '@/core/config/share-link-config';
import { toArticleDetailView, toArticleShareContent } from '@/features/article-detail/article-detail-view';
import { toAppPathFromIncomingLink } from '@/features/navigation/incoming-link-route';

import { NOW, nvdItem } from './briefing-item-fixtures';

const article = toArticleDetailView(
  { article: nvdItem, keyPoints: [], isAiSummarized: false, isSummaryPending: false, facts: [], relatedItems: [], responseGuideSections: [] },
  NOW,
).article;
const expectedSharedUrl = `${apiConfig.baseUrl}/a/${nvdItem.id}`;

describe('공유 주소와 공유 내용', () => {
  test('공유 주소는 API 주소 + /a/{기사 id}', () => {
    expect(toSharedArticleUrl('123')).toBe(`${apiConfig.baseUrl}/a/123`);
  });
  test('iOS 는 글에 제목, url 칸에 공유 주소', () => {
    expect(toArticleShareContent(article, 'ios')).toEqual({ message: nvdItem.title, url: expectedSharedUrl });
  });
  test('Android 는 url 칸이 없어 제목 다음 줄에 공유 주소', () => {
    expect(toArticleShareContent(article, 'android')).toEqual({ message: `${nvdItem.title}\n${expectedSharedUrl}` });
  });
  test('공유 내용에 원문 링크를 넣지 않는다 (원문은 링크 복사로)', () => {
    for (const platformOs of ['ios', 'android']) {
      expect(JSON.stringify(toArticleShareContent(article, platformOs))).not.toContain(nvdItem.originalUrl);
    }
  });
});

describe('들어온 링크 > 앱 경로', () => {
  test.each([
    ['https://api.secbrief.suhsaechan.kr/a/123', '/articles/123'],
    ['https://api.secbrief.suhsaechan.kr/a/123/', '/articles/123'],
    ['https://api.secbrief.suhsaechan.kr/a/123?utm_source=kakao', '/articles/123'],
    ['/a/45', '/articles/45'],
    ['secbrief:///a/77', '/articles/77'],
  ])('공유 링크 %s > %s', (incomingLink, expectedPath) => {
    expect(toAppPathFromIncomingLink(incomingLink)).toBe(expectedPath);
  });
  test.each([
    'secbrief://articles/123',
    '/articles/123',
    'https://api.secbrief.suhsaechan.kr/a/abc',
    'https://api.secbrief.suhsaechan.kr/api/v1/articles/1',
    '/notifications',
  ])('공유 링크가 아니면 그대로: %s', (incomingLink) => {
    expect(toAppPathFromIncomingLink(incomingLink)).toBe(incomingLink);
  });
});
