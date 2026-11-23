import type { BriefingTopic } from './briefing-item';

/** 주간 동향 주제 한 건: 이번 주 건수 <-> 지난주 같은 경과 구간 건수 */
export interface TrendTopic {
  topic: BriefingTopic;
  weeklyCount: number;
  previousWeekCount: number;
  /** 지난주 대비 증감 (단위: %, 줄면 음수, 지난주 0건이면 없음) */
  changePercent?: number;
}

/** 이번 주 동향: 주 시작일(월요일) + 주제 8종 (이번 주 건수 내림차순) */
export interface WeeklyTrend {
  weekStartDate: Date;
  topics: TrendTopic[];
}
