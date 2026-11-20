import type { BriefingItem } from './briefing-item';
import type { TrendTopic } from './trend-topic';

/** 오늘 탭 한 화면 분량: 읽은 현황 + 긴급 소식 + 동향 1위 + 시간순 소식 */
export interface TodayBriefing {
  date: Date;
  /** 오늘 브리핑 중 읽은 건수 / 전체 건수 */
  readCount: number;
  totalCount: number;
  /** 연속으로 확인한 일수 */
  streakDays: number;
  /** 긴급 소식 (없는 날은 undefined) */
  urgentItem?: BriefingItem;
  /** 이번 주 가장 많이 나온 주제 (집계 전이면 undefined) */
  topTrend?: TrendTopic;
  /** 시간순 소식 (최신이 먼저) */
  streamItems: BriefingItem[];
}

/** 보여 줄 소식이 하나도 없는지 */
export function isTodayBriefingEmpty(briefing: TodayBriefing): boolean {
  return briefing.urgentItem === undefined && briefing.streamItems.length === 0;
}
