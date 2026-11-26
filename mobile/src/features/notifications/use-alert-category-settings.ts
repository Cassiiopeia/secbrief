import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { type AppFailure, PUSH_PERMISSION_DENIED_ERROR_CODE, toAppFailure } from '@/core/error/app-failure';
import type { AlertCategory } from '@/data/models/alert-category';
import { readEnabledAlertCategories } from '@/data/sources/alert-category-storage';
import { getPushUnavailableReason } from '@/data/sources/device-notification-client';

import { showSbToast } from '@/design';

import { ALERT_CATEGORY_SAVED_MESSAGE, toToggledAlertCategories } from './alert-category-view-state';
import { saveAlertCategories } from './push-registration-service';

// React Query 캐시 키: 기기에 저장된 켠 알림 종류
export const ENABLED_ALERT_CATEGORIES_QUERY_KEY = ['enabled-alert-categories'] as const;

const NO_ALERT_CATEGORIES: AlertCategory[] = [];

/**
 * 알림 종류 스위치 상태: 기기 저장값 조회 + 스위치 바꾸기 (저장하는 동안 바꾼 값을 먼저 보여 주고, 성공하면 알림 막대, 실패하면 이전 값)
 */
export function useAlertCategorySettings() {
  const queryClient = useQueryClient();
  const storedQuery = useQuery({ queryKey: ENABLED_ALERT_CATEGORIES_QUERY_KEY, queryFn: readEnabledAlertCategories });
  const saveMutation = useMutation({
    mutationFn: saveAlertCategories,
    onSuccess: (savedAlertCategories) => {
      queryClient.setQueryData(ENABLED_ALERT_CATEGORIES_QUERY_KEY, savedAlertCategories);
      showSbToast(ALERT_CATEGORY_SAVED_MESSAGE);
    },
  });

  const storedAlertCategories = storedQuery.data ?? NO_ALERT_CATEGORIES;
  // 저장 중에는 요청한 값, 그 밖(성공·실패)에는 기기 저장값 > 실패하면 스위치가 저절로 이전 값
  const enabledAlertCategories = saveMutation.isPending ? saveMutation.variables : storedAlertCategories;
  const failure: AppFailure | undefined = saveMutation.isError ? toAppFailure(saveMutation.error) : undefined;
  const isPermissionDenied = failure?.errorCode === PUSH_PERMISSION_DENIED_ERROR_CODE;

  return {
    enabledAlertCategories,
    unavailableReason: getPushUnavailableReason(),
    isSaving: saveMutation.isPending,
    isPermissionDenied,
    /** 권한 거부를 뺀 저장 실패 (다시 시도 가능) */
    saveFailure: isPermissionDenied ? undefined : failure,
    onAlertCategoryToggled: (category: AlertCategory, isOn: boolean) =>
      saveMutation.mutate(toToggledAlertCategories(storedAlertCategories, category, isOn)),
    onSaveRetried: () => {
      if (saveMutation.variables !== undefined) saveMutation.mutate(saveMutation.variables);
    },
  };
}
