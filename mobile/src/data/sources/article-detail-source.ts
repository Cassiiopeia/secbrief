import { type ArticleDetailApiResponse, toArticleDetail } from '../mappers/api-response-mapper';
import type { ArticleDetail } from '../models/article-detail';
import { fetchApiData } from './secbrief-api-client';

/** 기사 상세 조회: GET /api/v1/articles/{id} */
export async function fetchArticleDetail(articleId: string): Promise<ArticleDetail> {
  const response = await fetchApiData<ArticleDetailApiResponse>(`/api/v1/articles/${encodeURIComponent(articleId)}`);
  return toArticleDetail(response);
}
