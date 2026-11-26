/** 많이 나온 키워드 한 건 (# 없는 키워드 + 그 키워드가 붙은 소식 수 + 순위 변화) */
export interface TrendingKeyword {
  keyword: string;
  articleCount: number;
  /** 순위 (1 부터) */
  rank: number;
  /** 지난 기간에는 없던 키워드 */
  isNew: boolean;
  /** 순위 변화 (양수 상승, 음수 하락, 0 유지). 신규이거나 서버가 안 주면 없음 */
  rankChange?: number;
}
