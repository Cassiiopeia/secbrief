/**
 * 오늘 탭 주요 소식 고르기: AI 가 고른 것 우선, 없으면 대체 규칙, 긴급 타일·스트림과 겹치지 않기
 */
import type { BriefingItem } from '@/data/models/briefing-item';
import type { TodayBriefing } from '@/data/models/today-briefing';
import { HEADLINE_MAXIMUM_COUNT, selectTodayHeadlines } from '@/features/today/today-headline-selection';
import { toTodayViewState } from '@/features/today/today-view-state';

import { NOW, nvdItem, overseasNewsItem } from './briefing-item-fixtures';

const toNewsItem = (id: string, overrides: Partial<BriefingItem> = {}): BriefingItem => ({ ...overseasNewsItem, id, ...overrides });
const toCveItem = (id: string, overrides: Partial<BriefingItem> = {}): BriefingItem => ({ ...nvdItem, id, ...overrides });

function createBriefing(overrides: Partial<TodayBriefing> = {}): TodayBriefing {
  return { date: new Date(2026, 10, 23), todayCount: 0, weeklyCount: 0, headlineItems: [], streamItems: [], ...overrides };
}

test('AI 가 고른 소식이 있으면 그것을 쓰고 스트림에서는 뺀다', () => {
  const headlineItem = toNewsItem('h1', { importance: 'headline' });
  const selection = selectTodayHeadlines(
    createBriefing({ headlineItems: [headlineItem], streamItems: [headlineItem, toNewsItem('s1'), toNewsItem('s2')] }),
  );
  expect(selection.isAiPicked).toBe(true);
  expect(selection.headlineItems.map((item) => item.id)).toEqual(['h1']);
  expect(selection.streamItems.map((item) => item.id)).toEqual(['s1', 's2']);
});
test('headlineItems 가 전부 routine 이면 대체 규칙: 악용 중 > 뉴스 > 그 밖, 스트림에 3건은 남긴다', () => {
  const streamItems = [toCveItem('c1'), toNewsItem('n1'), toCveItem('k1', { isKev: true }), toCveItem('c2'), toNewsItem('n2')];
  const selection = selectTodayHeadlines(createBriefing({ headlineItems: [toNewsItem('r1')], streamItems }));
  expect(selection.isAiPicked).toBe(false);
  expect(selection.headlineItems.map((item) => item.id)).toEqual(['k1', 'n1']);
  expect(selection.streamItems.map((item) => item.id)).toEqual(['c1', 'c2', 'n2']);
});
test('대체로 채울 소식이 2건 미만이면 구역을 만들지 않고 스트림을 그대로 둔다', () => {
  const streamItems = [toNewsItem('n1'), toNewsItem('n2'), toNewsItem('n3'), toNewsItem('n4')];
  const selection = selectTodayHeadlines(createBriefing({ streamItems }));
  expect(selection.headlineItems).toEqual([]);
  expect(selection.streamItems).toHaveLength(4);
});
test('긴급 타일에 나온 소식은 주요 소식·스트림 어디에도 다시 나오지 않는다', () => {
  const urgentItem = toCveItem('u1', { importance: 'headline' });
  const selection = selectTodayHeadlines(
    createBriefing({ urgentItem, headlineItems: [urgentItem, toNewsItem('h2', { importance: 'notable' })], streamItems: [urgentItem] }),
  );
  expect(selection.headlineItems.map((item) => item.id)).toEqual(['h2']);
  expect(selection.streamItems).toEqual([]);
});
test('주요 소식은 6건까지, 중복은 한 번만', () => {
  const headlineItems = Array.from({ length: 9 }, (_, index) => toNewsItem(`h${index % 8}`, { importance: 'notable' }));
  expect(selectTodayHeadlines(createBriefing({ headlineItems })).headlineItems).toHaveLength(HEADLINE_MAXIMUM_COUNT);
});
test('화면 상태: AI 가 골랐으면 그 사실을 제목에 밝히고, 대체 규칙이면 다른 제목', () => {
  const aiState = toTodayViewState(createBriefing({ headlineItems: [toNewsItem('h1', { importance: 'headline' })] }), NOW);
  expect(aiState.headline).toMatchObject({ title: 'AI 가 고른 주요 소식', isAiPicked: true });
  const streamItems = Array.from({ length: 6 }, (_, index) => toNewsItem(`n${index}`));
  const fallbackState = toTodayViewState(createBriefing({ streamItems }), NOW);
  expect(fallbackState.headline).toMatchObject({ title: '지금 주목할 소식', isAiPicked: false });
  expect(fallbackState.headline?.cards).toHaveLength(3);
  expect(fallbackState.streamItems).toHaveLength(3);
  expect(toTodayViewState(createBriefing(), NOW).headline).toBeUndefined();
});
test('주제 바로가기는 일반을 뺀 8개', () => {
  const shortcuts = toTodayViewState(createBriefing(), NOW).topicShortcuts;
  expect(shortcuts.map((shortcut) => shortcut.label)).toEqual(['취약점', '랜섬웨어', '개인정보', '원격 접속', '데이터베이스', '공급망', '정책·규제', '행사']);
  expect(shortcuts[0].key).toBe('topic-vulnerability');
});
