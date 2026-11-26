import { toNewArticleCounts } from '../mappers/new-article-counts-mapper';
import type { NewArticleCounts } from '../models/new-article-counts';
import { INVALID_RESPONSE_ERROR_CODE, AppFailure } from '@/core/error/app-failure';
import { fetchApiData } from './secbrief-api-client';

/** 오늘 새 소식 건수: GET /api/v1/articles/new-counts?timeZone= (읽지 못하면 응답 오류) */
export async function fetchNewArticleCounts(timeZone: string): Promise<NewArticleCounts> {
  const counts = toNewArticleCounts(await fetchApiData<unknown>('/api/v1/articles/new-counts', { timeZone }));
  if (counts === undefined) throw new AppFailure(INVALID_RESPONSE_ERROR_CODE, '서버 응답을 읽지 못했어요.');
  return counts;
}
