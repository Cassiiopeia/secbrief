/**
 * 소식 목록 규칙: 필터 재확인(중요도·출처·키워드), 표현 크기(중요도·목록 쓰임·게시 후 지난 날), 목록 배지, 주제 대표 그림,
 * 읽은 기사 목록, 모아 보기 묶음, 행사 타임라인 (경보단계 띠는 alert-level-dashboard.test.ts)
 */
import { toArticleBadges, toFeedBadges } from '@/features/articles/article-badges';
import { toTopicCoverKind } from '@/features/articles/article-cover';
import { type ArticleFeedPlacement, toArticleFeedPresentation } from '@/features/articles/article-feed-layout';
import { isArticleMatchingFilter } from '@/features/articles/article-filter-guard';
import { READ_ARTICLE_MAXIMUM_COUNT, toReadArticleIdsWith } from '@/features/articles/read-article-ids';
import { toBrowseHref, toBrowseTarget, toKeywordBrowseKey, toSourceBrowseKey, toTopicBrowseKey } from '@/features/browse/browse-target';
import { toEventTimelineGroups } from '@/features/explore/event-timeline';

import { NOW, kevOnlyItem, kisaItem, nvdItem, overseasNewsItem } from './briefing-item-fixtures';

describe('필터 재확인', () => {
  test('중요도 하한: notable 이면 notable·headline 만', () => {
    const filter = { minimumImportance: 'notable' } as const;
    expect(isArticleMatchingFilter({ ...overseasNewsItem, importance: 'headline' }, filter)).toBe(true);
    expect(isArticleMatchingFilter({ ...overseasNewsItem, importance: 'notable' }, filter)).toBe(true);
    expect(isArticleMatchingFilter(overseasNewsItem, filter)).toBe(false);
  });
  test('출처: 코드가 같을 때만. 코드가 없는 응답은 KEV 만 등재 여부로 판단', () => {
    expect(isArticleMatchingFilter({ ...kisaItem, sourceCode: 'KISA_REPORT' }, { sourceCode: 'KISA_REPORT' })).toBe(true);
    expect(isArticleMatchingFilter({ ...kisaItem, sourceCode: 'KISA' }, { sourceCode: 'KISA_REPORT' })).toBe(false);
    expect(isArticleMatchingFilter(kisaItem, { sourceCode: 'KISA_REPORT' })).toBe(false);
    expect(isArticleMatchingFilter(kevOnlyItem, { sourceCode: 'CISA_KEV' })).toBe(true);
    expect(isArticleMatchingFilter(nvdItem, { sourceCode: 'CISA_KEV' })).toBe(false);
  });
  test('긴급만: 긴급 소식만 (긴급 필터를 모르는 이전 서버 응답도 앱에서 거름)', () => {
    expect(isArticleMatchingFilter({ ...overseasNewsItem, isUrgent: true }, { isUrgent: true })).toBe(true);
    expect(isArticleMatchingFilter({ ...overseasNewsItem, isUrgent: false }, { isUrgent: true })).toBe(false);
  });
  test('키워드: 그 키워드가 붙은 소식만. 조건이 없으면 전부 통과', () => {
    expect(isArticleMatchingFilter({ ...overseasNewsItem, keywords: ['VPN'] }, { keyword: 'VPN' })).toBe(true);
    expect(isArticleMatchingFilter(overseasNewsItem, { keyword: 'VPN' })).toBe(false);
    expect(isArticleMatchingFilter(overseasNewsItem, { kind: 'news' })).toBe(true);
  });
});

describe('표현 크기', () => {
  const freshPlacement: ArticleFeedPlacement = { importance: 'routine', publishedAt: kisaItem.publishedAt, listPurpose: 'feed', isLead: false };
  const toPresentation = (overrides: Partial<ArticleFeedPlacement>) => toArticleFeedPresentation({ ...freshPlacement, ...overrides }, NOW);

  test('피드: headline 큰 카드, notable 중간 카드, routine 조밀한 행, 대표 자리는 항상 큰 카드', () => {
    expect(toPresentation({ importance: 'headline' })).toBe('headline');
    expect(toPresentation({ importance: 'notable' })).toBe('card');
    expect(toPresentation({ importance: 'routine' })).toBe('compact');
    expect(toPresentation({ importance: 'routine', isLead: true })).toBe('headline');
  });
  test('찾는 목록은 중요도·대표 자리와 상관없이 조밀한 행', () => {
    expect(toPresentation({ importance: 'headline', listPurpose: 'lookup' })).toBe('compact');
    expect(toPresentation({ importance: 'notable', isLead: true, listPurpose: 'lookup' })).toBe('compact');
  });
  test('게시 후 7일이 지나면 큰 카드로 만들지 않는다 (headline 은 중간 카드, 대표 자리는 제 중요도대로)', () => {
    const stalePublishedAt = new Date(2026, 10, 15, 9, 12);
    expect(toPresentation({ importance: 'headline', publishedAt: stalePublishedAt })).toBe('card');
    expect(toPresentation({ importance: 'routine', isLead: true, publishedAt: stalePublishedAt })).toBe('compact');
    expect(toPresentation({ importance: 'headline', publishedAt: new Date(2026, 10, 17, 9, 12) })).toBe('headline');
  });
});

describe('목록 배지', () => {
  const urgentKevItem = { ...nvdItem, isKev: true, translatedTitle: '원격 코드 실행', subjectName: 'Example VPN' };

  test('2개까지, 강한 것부터. 번역 항목도 AI 번역 배지 없음 (번역 아이콘으로)', () => {
    expect(toFeedBadges(toArticleBadges(urgentKevItem), false).map((badge) => badge.label)).toEqual(['긴급', '악용 중']);
    expect(toFeedBadges(toArticleBadges({ ...overseasNewsItem, translatedTitle: '번역 제목' }), false).map((badge) => badge.label)).toEqual(['뉴스']);
  });
  test('자리만 봐도 아는 분류는 뺀다 (취약점 탭의 취약점)', () => {
    const plainVulnerabilityItem = { ...nvdItem, isUrgent: false, topic: 'vulnerability' as const };
    expect(toArticleBadges(plainVulnerabilityItem).map((badge) => badge.label)).toEqual(['취약점']);
    expect(toFeedBadges(toArticleBadges(plainVulnerabilityItem), false, '취약점')).toEqual([]);
    expect(toFeedBadges(toArticleBadges({ ...nvdItem, isUrgent: false }), false, '취약점').map((badge) => badge.label)).toEqual(['원격 접속']);
  });
  test('조밀한 행은 분류 배지 없이 강한 배지만', () => {
    expect(toFeedBadges(toArticleBadges(kisaItem), true)).toEqual([]);
    expect(toFeedBadges(toArticleBadges({ ...kisaItem, isUrgent: true, subjectName: 'Example' }), true).map((badge) => badge.tone)).toEqual([
      'urgent',
      'subject',
    ]);
  });
});

describe('주제 대표 그림', () => {
  test('주제 대표 그림은 주제마다 다르다 (일반 제외 8종)', () => {
    const topics = ['vulnerability', 'ransomware', 'privacy', 'remoteAccess', 'database', 'supplyChain', 'policy', 'event'] as const;
    expect(new Set(topics.map(toTopicCoverKind)).size).toBe(topics.length);
  });
});

test('읽은 기사: 맨 뒤에 더하고, 다시 읽으면 맨 뒤로, 한도를 넘으면 오래된 것부터 버림', () => {
  expect(toReadArticleIdsWith(['1', '2'], '3')).toEqual(['1', '2', '3']);
  expect(toReadArticleIdsWith(['1', '2', '3'], '1')).toEqual(['2', '3', '1']);
  const fullIds = Array.from({ length: READ_ARTICLE_MAXIMUM_COUNT }, (_, index) => String(index));
  const nextIds = toReadArticleIdsWith(fullIds, 'new');
  expect(nextIds).toHaveLength(READ_ARTICLE_MAXIMUM_COUNT);
  expect(nextIds[0]).toBe('1');
  expect(nextIds[nextIds.length - 1]).toBe('new');
});

describe('모아 보기 묶음', () => {
  test('주제·출처·키워드·KISA 전체', () => {
    expect(toBrowseTarget(toTopicBrowseKey('ransomware'))).toEqual({ title: '랜섬웨어', filter: { topic: 'ransomware' } });
    expect(toBrowseTarget(toSourceBrowseKey('KISA_REPORT'))).toEqual({ title: 'KISA 보고서·가이드', filter: { sourceCode: 'KISA_REPORT' } });
    expect(toBrowseTarget(toKeywordBrowseKey('제로데이'))).toEqual({ title: '#제로데이', filter: { keyword: '제로데이' } });
    expect(toBrowseTarget('urgent')).toEqual({ title: '긴급 소식', filter: { isUrgent: true } });
    expect(toBrowseTarget('kisa-all')).toEqual({ title: 'KISA 소식', filter: { kind: 'advisory', region: 'domestic' } });
  });
  test('모르는 묶음은 없음', () => {
    expect(toBrowseTarget('topic-general')).toBeUndefined();
    expect(toBrowseTarget('source-UNKNOWN')).toBeUndefined();
    expect(toBrowseTarget('keyword- ')).toBeUndefined();
    expect(toBrowseTarget('')).toBeUndefined();
  });
  test('경로는 한글 키워드를 인코딩한다', () => {
    expect(toBrowseHref(toKeywordBrowseKey('랜섬웨어'))).toBe(`/browse/${encodeURIComponent('keyword-랜섬웨어')}`);
  });
});

test('행사 타임라인: 게시일이 같은 소식끼리 묶고, 위에서 정한 건수까지만', () => {
  const toEventItem = (id: string, day: number) => ({ ...overseasNewsItem, id, topic: 'event' as const, publishedAt: new Date(2026, 10, day, 10, 0) });
  const groups = toEventTimelineGroups([toEventItem('a', 23), toEventItem('b', 23), toEventItem('c', 21), toEventItem('d', 20)], NOW, 3);
  expect(groups.map((group) => group.dateLabel)).toEqual(['11.23 MON', '11.21 SAT']);
  expect(groups.map((group) => group.articles.map((article) => article.id))).toEqual([['a', 'b'], ['c']]);
});
