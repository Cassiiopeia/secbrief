import type { TrendTopic } from '@/data/models/trend-topic';

/** 주간 동향 순위 (탐색 탭): 순위는 주제가 정해진 소식만으로 매기고, 일반과 0건 주제는 따로 둠 */
export interface WeeklyTrendRanking {
  /** 1위 주제 (일반 제외. 소식이 있는 주제가 없으면 없음) */
  topTopic?: TrendTopic;
  /** 2위부터 (소식이 있는 주제만, 건수 많은 순) */
  otherTopics: TrendTopic[];
  /** 이번 주 소식이 없는 주제 (일반 제외) */
  quietTopics: TrendTopic[];
  /** 일반 (주제를 나누지 않은 소식). 0건이면 없음 */
  generalTopic?: TrendTopic;
  /** 일반까지 모두 0건 */
  isEmpty: boolean;
}

/** 주제별 건수 > 순위: 일반 제외 > 건수 많은 순(같으면 받은 순서) > 1위 / 나머지 / 0건 주제로 나눔 */
export function toWeeklyTrendRanking(topics: TrendTopic[]): WeeklyTrendRanking {
  const generalTopic = topics.find((topic) => topic.topic === 'general' && topic.weeklyCount > 0);
  const namedTopics = topics.filter((topic) => topic.topic !== 'general');
  const [topTopic, ...otherTopics] = namedTopics
    .filter((topic) => topic.weeklyCount > 0)
    .sort((left, right) => right.weeklyCount - left.weeklyCount);
  return {
    topTopic,
    otherTopics,
    quietTopics: namedTopics.filter((topic) => topic.weeklyCount <= 0),
    generalTopic,
    isEmpty: topTopic === undefined && generalTopic === undefined,
  };
}
