import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import { type AppFailure, toAppFailure } from '@/core/error/app-failure';
import { DEFAULT_QUIET_HOURS, type QuietHours } from '@/data/models/quiet-hours';
import { readStoredQuietHours } from '@/data/sources/quiet-hours-storage';
import { showSbToast } from '@/design';

import { saveQuietHours } from './push-registration-service';
import {
  type QuietHoursEdge,
  isQuietHoursSameTime,
  toQuietHoursSavedMessage,
  toQuietHoursWithTime,
  toToggledQuietHours,
} from './quiet-hours-view-state';

// React Query 캐시 키: 기기에 저장된 방해 금지 시간
export const QUIET_HOURS_QUERY_KEY = ['quiet-hours'] as const;

/**
 * 방해 금지 시간 상태: 기기 저장값 조회 + 스위치·시각 바꾸기 (저장하는 동안 바꾼 값을 먼저 보여 주고, 실패하면 이전 값)
 * 시작과 끝이 같아지는 변경은 보내지 않고 안내만 (isSameTimeRejected)
 */
export function useQuietHoursSettings() {
  const queryClient = useQueryClient();
  const storedQuery = useQuery({ queryKey: QUIET_HOURS_QUERY_KEY, queryFn: readStoredQuietHours });
  const [isSameTimeRejected, setIsSameTimeRejected] = useState(false);
  const saveMutation = useMutation({
    mutationFn: saveQuietHours,
    onSuccess: (savedQuietHours) => {
      // 저장 전 값과 비교해 무엇이 바뀌었는지 알림 막대로
      const previousQuietHours = queryClient.getQueryData<QuietHours>(QUIET_HOURS_QUERY_KEY) ?? DEFAULT_QUIET_HOURS;
      queryClient.setQueryData(QUIET_HOURS_QUERY_KEY, savedQuietHours);
      showSbToast(toQuietHoursSavedMessage(previousQuietHours, savedQuietHours));
    },
  });

  const storedQuietHours = storedQuery.data ?? DEFAULT_QUIET_HOURS;
  const quietHours = saveMutation.isPending ? saveMutation.variables : storedQuietHours;
  const saveFailure: AppFailure | undefined = saveMutation.isError ? toAppFailure(saveMutation.error) : undefined;

  const requestSave = (nextQuietHours: QuietHours) => {
    const isRejected = nextQuietHours.isEnabled && isQuietHoursSameTime(nextQuietHours);
    setIsSameTimeRejected(isRejected);
    if (!isRejected) saveMutation.mutate(nextQuietHours);
  };

  return {
    quietHours,
    isSaving: saveMutation.isPending,
    isSameTimeRejected,
    saveFailure,
    onQuietHoursToggled: (isOn: boolean) => requestSave(toToggledQuietHours(storedQuietHours, isOn)),
    onTimeSelected: (edge: QuietHoursEdge, timeOfDay: string) => requestSave(toQuietHoursWithTime(storedQuietHours, edge, timeOfDay)),
    onSaveRetried: () => {
      if (saveMutation.variables !== undefined) saveMutation.mutate(saveMutation.variables);
    },
  };
}
