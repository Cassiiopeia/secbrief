import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { type AppFailure, toAppFailure } from '@/core/error/app-failure';
import type { AlertDigestSettings, AlertInterests } from '@/data/models/alert-interest';
import { getDeviceTimeZone } from '@/data/sources/device-time-zone';
import { fetchAlertEstimate } from '@/data/sources/push-device-source';
import { showSbToast } from '@/design';

import { useDebouncedValue } from '../search/use-debounced-value';
import { saveAlertInterestSettings } from '../notifications/push-registration-service';
import { useAlertCategorySettings } from '../notifications/use-alert-category-settings';
import { type AlertInterestSettingChange, toAlertInterestSavedMessage } from './alert-interest-saved-messages';
import { ALERT_DIGEST_QUERY_KEY, ALERT_INTERESTS_QUERY_KEY, useAlertDigestSettings, useAlertInterests } from './use-alert-interests';

// 고르는 동안 추정을 다시 묻기까지 기다리는 시간 (0.5초)
const ESTIMATE_DEBOUNCE_MILLISECONDS = 500;

/**
 * 관심 기술·받는 방식 저장 (관심 기술 화면·더보기 아침 요약이 함께 씀): 성공하면 기기 캐시 갱신 + 바꾼 설정 종류별 알림 막대
 * 다시 시도는 실패한 요청 그대로 (바꾸려던 값과 종류)
 */
export function useAlertInterestSaver(onSaved?: () => void) {
  const queryClient = useQueryClient();
  const saveMutation = useMutation({
    mutationFn: ({ interests, digest }: { interests: AlertInterests; digest: AlertDigestSettings; change: AlertInterestSettingChange }) =>
      saveAlertInterestSettings(interests, digest),
    onSuccess: (result, { change }) => {
      queryClient.setQueryData(ALERT_INTERESTS_QUERY_KEY, result.interests);
      queryClient.setQueryData(ALERT_DIGEST_QUERY_KEY, result.digest);
      showSbToast(toAlertInterestSavedMessage(change, result.digest, result.isSentToServer));
      onSaved?.();
    },
  });
  const failure: AppFailure | undefined = saveMutation.isError ? toAppFailure(saveMutation.error) : undefined;
  return {
    isSaving: saveMutation.isPending,
    saveFailure: failure,
    save: (interests: AlertInterests, digest: AlertDigestSettings, change: AlertInterestSettingChange) =>
      saveMutation.mutate({ interests, digest, change }),
    retrySave: () => {
      if (saveMutation.variables !== undefined) saveMutation.mutate(saveMutation.variables);
    },
  };
}

/** 더보기의 받는 방식 바꾸기: 지금 관심 기술은 그대로 두고 방식(켬·끔)·시각만 저장 */
export function useAlertDigestSettingsEditor() {
  const interests = useAlertInterests();
  const digest = useAlertDigestSettings();
  const saver = useAlertInterestSaver();
  return {
    digest,
    isSaving: saver.isSaving,
    saveFailure: saver.saveFailure,
    saveDeliveryMode: (deliveryMode: AlertDigestSettings['deliveryMode']) => saver.save(interests, { ...digest, deliveryMode }, 'digestDeliveryMode'),
    saveDigestTime: (digestTime: string) => saver.save(interests, { ...digest, digestTime }, 'digestTime'),
    retrySave: saver.retrySave,
  };
}

/** 고르는 중인 설정의 어제 기준 알림 추정 (0.5초 멈추면 물음, 실패하면 없음) */
export function useAlertEstimate(interests: AlertInterests, digest: AlertDigestSettings) {
  const { enabledAlertCategories } = useAlertCategorySettings();
  // 글자로 바꿔 기다림 (그릴 때마다 새 객체가 생겨 기다림이 끝나지 않는 것 방지)
  const debouncedRequestKey = useDebouncedValue(
    JSON.stringify({ enabledAlertCategories, interests, digest, timeZone: getDeviceTimeZone() }),
    ESTIMATE_DEBOUNCE_MILLISECONDS,
  );
  return useQuery({
    queryKey: ['alert-estimate', debouncedRequestKey],
    queryFn: () => fetchAlertEstimate(JSON.parse(debouncedRequestKey)),
    retry: false,
  }).data;
}
