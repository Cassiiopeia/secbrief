import type { BriefingTopic } from '@/data/models/briefing-item';
import type { TrendTopic } from '@/data/models/trend-topic';

import { toTopicLabel } from '../articles/article-row-view';

/** 동향 주제 한 줄 표시 내용 (오늘 탭 1위 타일·동향 탭 목록 공통) */
export interface TrendTopicView {
  topic: BriefingTopic;
  topicLabel: string;
  detail: string;
}

/** 동향 주제 > 문구: 늘었어요 / 줄었어요 / 같아요 / 이번 주에 새로 나왔어요 / 0건 */
export function toTrendTopicView(trend: TrendTopic): TrendTopicView {
  return { topic: trend.topic, topicLabel: toTopicLabel(trend.topic), detail: toTrendDetail(trend) };
}

function toTrendDetail(trend: TrendTopic): string {
  const countText = `${trend.weeklyCount}건`;
  if (trend.changePercent === undefined) {
    return trend.weeklyCount > 0 ? `${countText} · 이번 주에 새로 나왔어요` : countText;
  }
  if (trend.changePercent > 0) return `${countText} · 지난주보다 ${trend.changePercent}% 늘었어요`;
  if (trend.changePercent < 0) return `${countText} · 지난주보다 ${-trend.changePercent}% 줄었어요`;
  return `${countText} · 지난주와 같아요`;
}
