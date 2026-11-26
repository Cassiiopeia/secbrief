import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';

import type { AppNotificationKind } from '@/data/models/app-notification';
import { type AppNotificationReadState, EMPTY_NOTIFICATION_READ_STATE } from '@/data/models/app-notification-read-state';
import { readNotificationReadState, saveNotificationReadState } from '@/data/sources/notification-read-storage';

import { toReadStateWithOpened, toReadStateWithSeen } from './notification-read-state';

// React Query 캐시 키: 기기에 저장된 알림함 읽음 기록
const NOTIFICATION_READ_STATE_QUERY_KEY = ['notification-read-state'] as const;

/** 알림함 읽음 기록 (기기 저장값, 앱이 켜져 있는 동안 캐시). isLoaded: 저장값을 다 읽었는지 (새 글 기준을 정하기 전에 확인) */
export function useNotificationReadState(): { readState: AppNotificationReadState; isLoaded: boolean } {
  const readStateQuery = useQuery({ queryKey: NOTIFICATION_READ_STATE_QUERY_KEY, queryFn: readNotificationReadState, staleTime: Infinity });
  return { readState: readStateQuery.data ?? EMPTY_NOTIFICATION_READ_STATE, isLoaded: readStateQuery.isSuccess };
}

/** 읽음 기록 바꾸는 함수 2개: 글을 열었을 때, 목록에서 글을 봤을 때 (캐시 먼저, 저장 실패는 조용히 넘김) */
export function useNotificationReadStateUpdaters() {
  const queryClient = useQueryClient();
  const updateReadState = useCallback(
    (toNextReadState: (readState: AppNotificationReadState) => AppNotificationReadState) => {
      // 기기 저장값을 먼저 읽어 온 뒤 바꿈 (앱을 켜자마자 들어와도 이전 기록을 덮어쓰지 않게)
      void queryClient
        .ensureQueryData({ queryKey: NOTIFICATION_READ_STATE_QUERY_KEY, queryFn: readNotificationReadState })
        .then((readState) => {
          const nextReadState = toNextReadState(readState);
          if (nextReadState === readState) return;
          queryClient.setQueryData(NOTIFICATION_READ_STATE_QUERY_KEY, nextReadState);
          return saveNotificationReadState(nextReadState);
        })
        .catch(() => undefined);
    },
    [queryClient],
  );
  return {
    markNotificationOpened: useCallback(
      (notificationId: number) => updateReadState((readState) => toReadStateWithOpened(readState, notificationId)),
      [updateReadState],
    ),
    markNotificationsSeen: useCallback(
      (kind: AppNotificationKind, notificationIds: number[]) =>
        updateReadState((readState) => toReadStateWithSeen(readState, kind, notificationIds)),
      [updateReadState],
    ),
  };
}
