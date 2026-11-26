/** 어제(기기 날짜) 수집분을 지금 고른 설정으로 다시 돌린 알림 추정 (#93) */
export interface AlertEstimate {
  /** 기준 날짜 (yyyy-MM-dd) */
  date: string;
  immediatePushCount: number;
  immediateArticleCount: number;
  digestArticleCount: number;
  digestPushCount: number;
  /** 어제 실제로 보낸 공통 알림 수 */
  currentPushCount: number;
}

/** 응답 > 추정 (숫자가 아닌 칸은 0, 객체가 아니면 없음) */
export function toAlertEstimate(value: unknown): AlertEstimate | undefined {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return undefined;
  const response = value as Record<string, unknown>;
  const toCount = (count: unknown) => (typeof count === 'number' && Number.isInteger(count) && count >= 0 ? count : 0);
  return {
    date: typeof response.date === 'string' ? response.date : '',
    immediatePushCount: toCount(response.immediatePushCount),
    immediateArticleCount: toCount(response.immediateArticleCount),
    digestArticleCount: toCount(response.digestArticleCount),
    digestPushCount: toCount(response.digestPushCount),
    currentPushCount: toCount(response.currentPushCount),
  };
}
