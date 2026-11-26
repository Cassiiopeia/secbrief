import type { ArticleSearchMode } from '@/data/models/article-search-result';

// 검색어 길이 한도 (서버 검증과 같은 값: 2자 이상 100자 이하)
export const SEARCH_QUERY_MINIMUM_LENGTH = 2;
export const SEARCH_QUERY_MAXIMUM_LENGTH = 100;
// 한 글자만 입력했을 때 입력창 아래 안내 (SEARCH_QUERY_MINIMUM_LENGTH 와 함께 바꿈)
export const SEARCH_QUERY_TOO_SHORT_NOTICE = '두 글자 이상 입력해 주세요.';

// 검색 방식 > 결과 위 한 줄 안내
const SEARCH_MODE_NOTICE_BY_MODE: Record<ArticleSearchMode, string> = {
  semantic: 'AI 가 뜻이 비슷한 소식을 찾았어요.',
  keyword: '글자가 들어간 소식을 찾았어요.',
};

/** 입력한 글 > 서버에 보낼 검색어 (앞뒤 공백 제거, 100자까지). 2자 미만이면 없음 (검색하지 않음) */
export function toSearchQuery(inputText: string): string | undefined {
  const trimmedText = inputText.trim().slice(0, SEARCH_QUERY_MAXIMUM_LENGTH);
  return trimmedText.length < SEARCH_QUERY_MINIMUM_LENGTH ? undefined : trimmedText;
}

/** 입력은 했지만 검색하기에는 짧은지 (빈 입력은 아님) */
export function isSearchQueryTooShort(inputText: string): boolean {
  const trimmedLength = inputText.trim().length;
  return trimmedLength > 0 && trimmedLength < SEARCH_QUERY_MINIMUM_LENGTH;
}

/** 검색 방식 > 안내 문구 */
export function toSearchModeNotice(searchMode: ArticleSearchMode): string {
  return SEARCH_MODE_NOTICE_BY_MODE[searchMode];
}
