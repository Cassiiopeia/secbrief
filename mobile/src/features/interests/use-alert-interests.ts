import { useQuery } from '@tanstack/react-query';

import { type AlertDigestSettings, type AlertInterests, DEFAULT_ALERT_DIGEST_SETTINGS, EMPTY_ALERT_INTERESTS } from '@/data/models/alert-interest';
import { readStoredAlertDigestSettings, readStoredAlertInterests } from '@/data/sources/alert-interest-storage';

// React Query 캐시 키: 기기에 저장된 관심 기술·받는 방식
export const ALERT_INTERESTS_QUERY_KEY = ['alert-interests'] as const;
export const ALERT_DIGEST_QUERY_KEY = ['alert-digest'] as const;

/** 기기에 저장된 관심 기술 (읽기 전에는 고른 것 없음) */
export function useAlertInterests(): AlertInterests {
  return useQuery({ queryKey: ALERT_INTERESTS_QUERY_KEY, queryFn: readStoredAlertInterests }).data ?? EMPTY_ALERT_INTERESTS;
}

/** 기기에 저장된 받는 방식 (읽기 전에는 아침 요약 08:00) */
export function useAlertDigestSettings(): AlertDigestSettings {
  return useQuery({ queryKey: ALERT_DIGEST_QUERY_KEY, queryFn: readStoredAlertDigestSettings }).data ?? DEFAULT_ALERT_DIGEST_SETTINGS;
}
