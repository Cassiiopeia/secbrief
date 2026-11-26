import { type ArticleSearchApiResponse, toArticleSearchResult } from '../mappers/api-response-mapper';
import type { ArticleSearchResult } from '../models/article-search-result';
import { fetchApiData } from './secbrief-api-client';

/** 소식 검색: GET /api/v1/articles/search (상위 size 건, 검색어는 주소 조립 때 인코딩됨) */
export async function fetchArticleSearch(query: string, size: number): Promise<ArticleSearchResult> {
  const response = await fetchApiData<ArticleSearchApiResponse>('/api/v1/articles/search', { query, size: String(size) });
  return toArticleSearchResult(response);
}
