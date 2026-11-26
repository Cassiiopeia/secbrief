import type { ArticleImportance } from '@/data/models/briefing-item';

/** 목록 안 표현 3종: headline 큰 카드, card 중간 카드, compact 조밀한 행 */
export type ArticleFeedPresentation = 'headline' | 'card' | 'compact';

/** 목록 쓰임 2종: feed 훑어보는 피드(홈·소식·취약점), lookup 찾는 목록(검색 결과·모아 보기·출처별 목록) */
export type ArticleListPurpose = 'feed' | 'lookup';

/** 소식이 목록에 놓이는 자리 (표현 크기를 고르는 기준 4개) */
export interface ArticleFeedPlacement {
  importance: ArticleImportance;
  publishedAt: Date;
  listPurpose: ArticleListPurpose;
  /** 목록 맨 위 대표 자리 */
  isLead: boolean;
}

// 큰 카드로 보여 주는 한도: 게시 후 7일 (단위: 밀리초)
const HEADLINE_FRESH_MILLISECONDS = 7 * 24 * 60 * 60 * 1000;

/**
 * 자리 > 표현 크기: 찾는 목록은 조밀한 행만, 피드는 headline 큰 카드 / notable 중간 카드 / routine 조밀한 행
 * 대표 자리는 중요도와 상관없이 큰 카드. 게시 후 7일이 지나면 큰 카드로 만들지 않음 (headline 은 중간 카드로)
 */
export function toArticleFeedPresentation(placement: ArticleFeedPlacement, now: Date): ArticleFeedPresentation {
  if (placement.listPurpose === 'lookup') return 'compact';
  const isFresh = now.getTime() - placement.publishedAt.getTime() <= HEADLINE_FRESH_MILLISECONDS;
  if (isFresh && (placement.isLead || placement.importance === 'headline')) return 'headline';
  return placement.importance === 'routine' ? 'compact' : 'card';
}
