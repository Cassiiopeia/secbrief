import type { BriefingItem } from './briefing-item';

/** 검색 방식: semantic 임베딩 의미 검색(뜻이 비슷한 소식), keyword 글자 일치 검색(임베딩 준비 전) */
export type ArticleSearchMode = 'semantic' | 'keyword';

/** 소식 검색 결과: 상위 N건 (다음 페이지 없음) + 서버가 쓴 검색 방식 */
export interface ArticleSearchResult {
  items: BriefingItem[];
  searchMode: ArticleSearchMode;
}
