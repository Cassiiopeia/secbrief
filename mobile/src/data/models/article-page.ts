import type { BriefingItem } from './briefing-item';

/** 소식 목록 한 페이지: 항목 + 다음 페이지 위치 (마지막이면 없음) */
export interface ArticlePage {
  items: BriefingItem[];
  nextCursor?: string;
}
