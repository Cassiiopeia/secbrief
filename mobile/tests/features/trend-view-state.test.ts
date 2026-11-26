/**
 * 동향 주제 표시: 문구 5종(늘었어요·줄었어요·같아요·새로 나왔어요·0건), 비율 막대, 증감 표시, 주간 순위(일반 제외)
 */
import type { TrendTopic } from '@/data/models/trend-topic';
import { toTrendShareRatio, toTrendTopicView } from '@/features/trend/trend-view-state';
import { toWeeklyTrendRanking } from '@/features/trend/weekly-trend-ranking';

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
test('비율 막대: 1위 대비 비율, 1위가 0건이면 0, 범위는 0~1', () => {
  expect(toTrendShareRatio(5, 20)).toBe(0.25);
  expect(toTrendShareRatio(0, 0)).toBe(0);
  expect(toTrendShareRatio(30, 20)).toBe(1);
});
test('증감 표시: 늘면 위 화살표, 줄면 아래 화살표, 같으면 줄표, 지난주 0건이면 NEW, 0건이면 없음', () => {
  const toChange = (weeklyCount: number, changePercent?: number) =>
    toTrendTopicView({ topic: 'ransomware', weeklyCount, previousWeekCount: 1, changePercent }).change;
  expect(toChange(12, 20)).toEqual({ direction: 'up', label: '20%' });
  expect(toChange(8, -20)).toEqual({ direction: 'down', label: '20%' });
  expect(toChange(4, 0)).toEqual({ direction: 'flat', label: '' });
  expect(toChange(3)).toEqual({ direction: 'new', label: 'NEW' });
  expect(toChange(0)).toBeUndefined();
  expect(toTrendTopicView({ topic: 'ransomware', weeklyCount: 12, previousWeekCount: 10, changePercent: 20 }).countLabel).toBe('12건');
});

describe('주간 순위', () => {
  const toTopic = (topic: TrendTopic['topic'], weeklyCount: number): TrendTopic => ({ topic, weeklyCount, previousWeekCount: 0 });

  test('1위는 일반을 빼고 고른다 (홈의 1위 주제와 같은 기준). 0건 주제와 일반은 따로', () => {
    const ranking = toWeeklyTrendRanking([
      toTopic('general', 536),
      toTopic('event', 5),
      toTopic('vulnerability', 3),
      toTopic('ransomware', 1),
      toTopic('privacy', 0),
      toTopic('policy', 0),
    ]);
    expect(ranking.topTopic?.topic).toBe('event');
    expect(ranking.otherTopics.map((topic) => topic.topic)).toEqual(['vulnerability', 'ransomware']);
    expect(ranking.quietTopics.map((topic) => topic.topic)).toEqual(['privacy', 'policy']);
    expect(ranking.generalTopic?.weeklyCount).toBe(536);
    expect(ranking.isEmpty).toBe(false);
  });
  test('주제가 정해진 소식이 없으면 1위 없음, 일반까지 0건이면 빈 상태', () => {
    expect(toWeeklyTrendRanking([toTopic('general', 4), toTopic('privacy', 0)])).toMatchObject({ topTopic: undefined, isEmpty: false });
    expect(toWeeklyTrendRanking([toTopic('general', 0), toTopic('privacy', 0)])).toMatchObject({ generalTopic: undefined, isEmpty: true });
  });
});
