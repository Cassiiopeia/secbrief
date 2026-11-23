/**
 * 동향 문구: 늘었어요·줄었어요·같아요·새로 나왔어요·0건
 */
import { toTrendTopicView } from '@/features/trend/trend-view-state';

test('동향 문구 4종', () => {
  expect(toTrendTopicView({ topic: 'ransomware', weeklyCount: 12, previousWeekCount: 10, changePercent: 20 }).detail).toBe(
    '12건 · 지난주보다 20% 늘었어요',
  );
  expect(toTrendTopicView({ topic: 'privacy', weeklyCount: 5, previousWeekCount: 10, changePercent: -50 }).detail).toBe(
    '5건 · 지난주보다 50% 줄었어요',
  );
  expect(toTrendTopicView({ topic: 'database', weeklyCount: 4, previousWeekCount: 4, changePercent: 0 }).detail).toBe(
    '4건 · 지난주와 같아요',
  );
  expect(toTrendTopicView({ topic: 'general', weeklyCount: 3, previousWeekCount: 0 }).detail).toBe('3건 · 이번 주에 새로 나왔어요');
});
test('이번 주·지난주 모두 0건', () => {
  expect(toTrendTopicView({ topic: 'policy', weeklyCount: 0, previousWeekCount: 0 }).detail).toBe('0건');
});
test('주제 이름이 함께 실린다', () => {
  expect(toTrendTopicView({ topic: 'supplyChain', weeklyCount: 1, previousWeekCount: 0 }).topicLabel).toBe('공급망');
});
