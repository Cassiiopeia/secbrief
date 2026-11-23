import type { ArticleKind, ArticleRegion } from './briefing-item';

/** 수집 출처 한 곳의 상태 (설정 탭 출처 목록) */
export interface CollectSourceStatus {
  code: string;
  displayName: string;
  kind: ArticleKind;
  region: ArticleRegion;
  /** 마지막으로 모은 시각 (한 번도 못 모았으면 없음) */
  lastCollectedAt?: Date;
  isLastCollectSucceeded: boolean;
}
