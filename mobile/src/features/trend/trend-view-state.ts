import type { BriefingTopic } from '@/data/models/briefing-item';
import type { TrendTopic } from '@/data/models/trend-topic';
import type { SbRatioChange } from '@/design';

import { toTopicLabel } from '../articles/article-row-view';

/** 동향 주제 한 줄 표시 내용 (홈 1위 타일·탐색 탭 주간 동향 공통) */
export interface TrendTopicView {
  topic: BriefingTopic;
  topicLabel: string;
  detail: string;
  /** 예: 12건 */
  countLabel: string;
  /** 지난주 대비 증감 표시 (0건이면 없음) */
  change?: SbRatioChange;
}

/** 동향 주제 > 문구: 늘었어요 / 줄었어요 / 같아요 / 이번 주에 새로 나왔어요 / 0건 */
export function toTrendTopicView(trend: TrendTopic): TrendTopicView {
  return {
    topic: trend.topic,
    topicLabel: toTopicLabel(trend.topic),
    detail: toTrendDetail(trend),
    countLabel: `${trend.weeklyCount}건`,
    change: toTrendChange(trend),
  };
}

/** 지난주 대비 증감 > 표시: 지난주 0건인데 이번 주에 나왔으면 NEW, 늘면 위 화살표 + %, 줄면 아래 화살표 + %, 같으면 줄표 */
function toTrendChange(trend: TrendTopic): SbRatioChange | undefined {
  if (trend.changePercent === undefined) return trend.weeklyCount > 0 ? { direction: 'new', label: 'NEW' } : undefined;
  if (trend.changePercent > 0) return { direction: 'up', label: `${trend.changePercent}%` };
  if (trend.changePercent < 0) return { direction: 'down', label: `${-trend.changePercent}%` };
  return { direction: 'flat', label: '' };
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

/** 주제 건수 > 가장 많은 주제 대비 비율 (0~1, 막대 길이용). 가장 많은 주제가 0건이면 0 */
export function toTrendShareRatio(weeklyCount: number, maximumWeeklyCount: number): number {
  if (maximumWeeklyCount <= 0) return 0;
  return Math.min(Math.max(weeklyCount / maximumWeeklyCount, 0), 1);
}
