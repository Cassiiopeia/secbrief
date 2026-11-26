import {
  type ArticlePageApiResponse,
  toApiImportance,
  toApiKind,
  toApiRegion,
  toApiTopic,
  toArticlePage,
} from '../mappers/api-response-mapper';
import type { ArticlePage } from '../models/article-page';
import type { ArticleFilter } from '../models/briefing-item';
import { fetchApiData } from './secbrief-api-client';

/** 소식 목록 한 페이지 조회: GET /api/v1/articles (필터 없으면 전체, cursor 는 다음 페이지 위치) */
export async function fetchArticlePage(filter: ArticleFilter, cursor?: string): Promise<ArticlePage> {
  const response = await fetchApiData<ArticlePageApiResponse>('/api/v1/articles', {
    kind: filter.kind && toApiKind(filter.kind),
    region: filter.region && toApiRegion(filter.region),
    topic: filter.topic && toApiTopic(filter.topic),
    minimumImportance: filter.minimumImportance && toApiImportance(filter.minimumImportance),
    sourceCode: filter.sourceCode,
    keyword: filter.keyword,
    isUrgent: filter.isUrgent ? 'true' : undefined,
    cursor,
  });
  return toArticlePage(response);
}
