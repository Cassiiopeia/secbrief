import { type TodayApiResponse, toTodayBriefing } from '../mappers/api-response-mapper';
import type { TodayBriefing } from '../models/today-briefing';
import { fetchApiData } from './secbrief-api-client';

/** 오늘 화면 조회: GET /api/v1/today */
export async function fetchTodayBriefing(): Promise<TodayBriefing> {
  return toTodayBriefing(await fetchApiData<TodayApiResponse>('/api/v1/today'));
}
