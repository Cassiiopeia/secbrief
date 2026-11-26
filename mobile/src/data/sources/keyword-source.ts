import { type TrendingKeywordsApiResponse, toTrendingKeywords } from '../mappers/api-response-mapper';
import type { TrendingKeyword } from '../models/trending-keyword';
import { fetchApiData } from './secbrief-api-client';

/** 많이 나온 키워드 조회: GET /api/v1/keywords/trending (최근 days 일, 건수 많은 순 size 개) */
export async function fetchTrendingKeywords(days: number, size: number): Promise<TrendingKeyword[]> {
  const response = await fetchApiData<TrendingKeywordsApiResponse>('/api/v1/keywords/trending', {
    days: String(days),
    size: String(size),
  });
  return toTrendingKeywords(response);
}
