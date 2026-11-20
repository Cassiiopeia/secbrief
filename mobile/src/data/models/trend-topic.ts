/** 주간 동향 주제 한 건: 이번 주 건수와 지난주 대비 변화 */
export interface TrendTopic {
  name: string;
  weeklyCount: number;
  /** 지난주 대비 증감 (단위: %, 줄면 음수) */
  changePercent: number;
}
