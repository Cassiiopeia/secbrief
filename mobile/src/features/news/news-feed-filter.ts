import type { Href } from 'expo-router';

import type { ArticleFilter, BriefingTopic } from '@/data/models/briefing-item';

import type { ImportanceView } from '../articles/use-importance-filtered-articles';
import { BROWSABLE_TOPICS } from '../browse/browse-target';
import { APP_ROUTES } from '../navigation/app-routes';

/** 소식 탭 카테고리 5종: important AI 가 고른 주요만, all 전체, domestic 국내, overseas 해외, event 행사 */
const NEWS_CATEGORIES = ['important', 'all', 'domestic', 'overseas', 'event'] as const;
export type NewsCategory = (typeof NEWS_CATEGORIES)[number];

/** 주제 칩 값: all 전체 또는 주제 하나 */
export type NewsTopicFilter = 'all' | BriefingTopic;

// 주제 칩에 놓는 주제 (행사는 카테고리 칩에 있어 뺌)
export const NEWS_CHIP_TOPICS: BriefingTopic[] = BROWSABLE_TOPICS.filter((topic) => topic !== 'event');

/** 소식 탭에서 고른 것: 카테고리 칩 + 주제 칩 */
export interface NewsSelection {
  category: NewsCategory;
  topicFilter: NewsTopicFilter;
}

/** 소식 탭 경로 파라미터 (예: /news?category=all&topic=ransomware). 고른 칩의 단일 원본 */
export type NewsRouteParams = {
  category?: string;
  topic?: string;
};

/** 소식 탭 조회 조건: 바탕 필터 + 중요도 보기 (주요만이면 important) */
export interface NewsFeedQuery {
  baseFilter: ArticleFilter;
  importanceView: ImportanceView;
}

/** 경로 파라미터 > 고른 칩 (없거나 모르는 값이면 주요만 · 모든 주제) */
export function toNewsSelection(routeParams: NewsRouteParams): NewsSelection {
  return {
    category: NEWS_CATEGORIES.find((category) => category === routeParams.category) ?? 'important',
    topicFilter: NEWS_CHIP_TOPICS.find((topic) => topic === routeParams.topic) ?? 'all',
  };
}

/** 고른 칩 > 경로 파라미터 */
export function toNewsRouteParams(selection: NewsSelection): Required<NewsRouteParams> {
  return { category: selection.category, topic: selection.topicFilter };
}

/** 소식 탭을 그 카테고리로 여는 경로 (주제 칩은 모든 주제로) */
export function toNewsHref(category: NewsCategory): Href {
  return { pathname: APP_ROUTES.news, params: toNewsRouteParams({ category, topicFilter: 'all' }) };
}

/**
 * 카테고리·주제 칩 > 조회 조건
 * 행사는 종류를 가리지 않고 주제가 행사인 소식 전부 (주제 칩은 쓰지 않음), 그 밖은 뉴스만 + 고른 주제
 */
export function toNewsFeedQuery(category: NewsCategory, topicFilter: NewsTopicFilter): NewsFeedQuery {
  if (category === 'event') return { baseFilter: { topic: 'event' }, importanceView: 'all' };
  const topic = topicFilter === 'all' ? undefined : topicFilter;
  const region = category === 'domestic' || category === 'overseas' ? category : undefined;
  return { baseFilter: { kind: 'news', region, topic }, importanceView: category === 'important' ? 'important' : 'all' };
}

/** 주제 칩을 보여 줄 카테고리인지 (행사는 주제가 이미 정해짐) */
export function hasTopicChips(category: NewsCategory): boolean {
  return category !== 'event';
}
