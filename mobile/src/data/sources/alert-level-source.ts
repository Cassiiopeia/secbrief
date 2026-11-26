import { type AlertLevelDashboardApiResponse, toAlertLevelDashboard } from '../mappers/alert-level-mapper';
import type { AlertLevelDashboard } from '../models/alert-level-dashboard';
import { fetchApiData } from './secbrief-api-client';

/** 경보단계 화면 조회: GET /api/v1/alert-level */
export async function fetchAlertLevelDashboard(): Promise<AlertLevelDashboard> {
  return toAlertLevelDashboard(await fetchApiData<AlertLevelDashboardApiResponse>('/api/v1/alert-level'));
}
