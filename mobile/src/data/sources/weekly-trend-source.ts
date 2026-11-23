import { type WeeklyTrendApiResponse, toWeeklyTrend } from '../mappers/api-response-mapper';
import type { WeeklyTrend } from '../models/trend-topic';
import { fetchApiData } from './secbrief-api-client';

/** 이번 주 동향 조회: GET /api/v1/trends/weekly */
export async function fetchWeeklyTrend(): Promise<WeeklyTrend> {
  return toWeeklyTrend(await fetchApiData<WeeklyTrendApiResponse>('/api/v1/trends/weekly'));
}
