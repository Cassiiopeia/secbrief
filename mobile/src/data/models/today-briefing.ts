import type { BriefingItem } from './briefing-item';
import type { TrendTopic } from './trend-topic';

/** 오늘 탭 한 화면 분량: 오늘·이번 주 건수 + 긴급 소식 + 동향 1위 + 최신 소식 */
export interface TodayBriefing {
  date: Date;
  todayCount: number;
  weeklyCount: number;
  /** 긴급 소식 (72시간 안에 없으면 undefined) */
  urgentItem?: BriefingItem;
  /** 이번 주 가장 많이 나온 주제 (일반 제외, 없으면 undefined) */
  topTrend?: TrendTopic;
  /** 최신 소식 (최신이 먼저, 긴급 소식 제외) */
  streamItems: BriefingItem[];
}

/** 보여 줄 소식이 하나도 없는지 */
export function isTodayBriefingEmpty(briefing: TodayBriefing): boolean {
  return briefing.urgentItem === undefined && briefing.streamItems.length === 0;
}
