/**
 * 오늘 화면 상태: 머리 라벨, 긴급 라벨·아래 줄·번역 표시, 새 주제 커버, 원문 주소, 동향 문구 단일 원본
 */
import type { TodayBriefing } from '@/data/models/today-briefing';
import type { TrendTopic } from '@/data/models/trend-topic';
import { toTodayViewState } from '@/features/today/today-view-state';
import { toTrendTopicView } from '@/features/trend/trend-view-state';

import { NOW, kevOnlyItem, nvdItem, overseasNewsItem } from './briefing-item-fixtures';

const nvdKevItem = { ...nvdItem, isKev: true };
const newsItem = overseasNewsItem;
const ransomwareTrend: TrendTopic = { topic: 'ransomware', weeklyCount: 12, previousWeekCount: 10, changePercent: 20 };

/** 기본 브리핑 + 바꿀 값 */
function createBriefing(overrides: Partial<TodayBriefing> = {}): TodayBriefing {
  return { date: new Date(2026, 10, 23), todayCount: 0, weeklyCount: 0, headlineItems: [], streamItems: [], ...overrides };
}

test('머리 라벨은 날짜와 오늘 건수', () => {
  expect(toTodayViewState(createBriefing({ todayCount: 12 }), NOW).mastheadTrailingLines).toEqual(['11.23 MON', '오늘 12건']);
});
test('KEV 만 있는 긴급 항목 라벨', () => {
  expect(toTodayViewState(createBriefing({ urgentItem: kevOnlyItem }), NOW).urgent?.capsLabel).toBe('긴급 / KEV 등재');
});
test('점수가 있으면 CVSS 라벨', () => {
  expect(toTodayViewState(createBriefing({ urgentItem: nvdItem }), NOW).urgent?.capsLabel).toBe('긴급 / CVSS 9.8');
});
test('긴급 아래 줄에 게시자와 날짜', () => {
  expect(toTodayViewState(createBriefing({ urgentItem: nvdKevItem }), NOW).urgent?.footerLabel).toBe(
    'NVD · CISA KEV 등재 · 2026.11.23 09:12 · 2시간 전',
  );
});
test('번역된 긴급 항목은 한국어 제목과 AI 번역 표시', () => {
  const urgent = toTodayViewState(createBriefing({ urgentItem: { ...nvdKevItem, translatedTitle: '원격 코드 실행 취약점' } }), NOW).urgent;
  expect(urgent?.title).toBe('원격 코드 실행 취약점');
  expect(urgent?.footerLabel).toMatch(/ · AI 번역$/);
});
test('새 주제도 커버 그림이 있다', () => {
  for (const topic of ['supplyChain', 'policy', 'general'] as const) {
    expect(toTodayViewState(createBriefing({ streamItems: [{ ...newsItem, topic }] }), NOW).streamItems[0].thumbnailKind).toBeDefined();
  }
});
test('스트림 항목에 원문 주소와 게시자·날짜가 실린다', () => {
  const streamItem = toTodayViewState(createBriefing({ streamItems: [newsItem] }), NOW).streamItems[0];
  expect(streamItem.article.originalUrl).toBe(newsItem.originalUrl);
  expect(streamItem.timeLabel).toBe('BleepingComputer · 2시간 전');
});
test('동향 1위 문구는 탐색 탭 주간 동향과 같다', () => {
  expect(toTodayViewState(createBriefing({ topTrend: ransomwareTrend }), NOW).trend?.detail).toBe(
    toTrendTopicView(ransomwareTrend).detail,
  );
  expect(toTodayViewState(createBriefing({ topTrend: ransomwareTrend }), NOW).trend).toMatchObject({ topicName: '랜섬웨어', coverKind: 'lock' });
});
test('이번 주 건수 타일', () => {
  expect(toTodayViewState(createBriefing({ weeklyCount: 40 }), NOW).weeklyCount).toBe(40);
});
test('스트림·긴급 항목에 대표 이미지 주소가 실린다 (없으면 없음)', () => {
  const state = toTodayViewState(
    createBriefing({ urgentItem: { ...nvdItem, imagePath: '/api/v1/articles/2/image' }, streamItems: [newsItem] }),
    NOW,
  );
  expect(state.urgent?.coverImageUrl).toMatch(/\/api\/v1\/articles\/2\/image$/);
  expect(state.streamItems[0].thumbnailImageUrl).toBeUndefined();
});
