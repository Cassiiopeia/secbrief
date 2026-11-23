import { type SourceStatusApiResponse, toCollectSourceStatus } from '../mappers/api-response-mapper';
import type { CollectSourceStatus } from '../models/collect-source-status';
import { fetchApiData } from './secbrief-api-client';

/** 수집 출처 상태 조회: GET /api/v1/sources */
export async function fetchCollectSourceStatuses(): Promise<CollectSourceStatus[]> {
  return (await fetchApiData<SourceStatusApiResponse[]>('/api/v1/sources')).map(toCollectSourceStatus);
}
