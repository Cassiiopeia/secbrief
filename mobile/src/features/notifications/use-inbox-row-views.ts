import { useEffect, useState } from 'react';

import type { AppNotificationSummary, InboxNotificationKind } from '@/data/models/app-notification';

import { type NotificationInboxRowView, toNotificationInboxRowView } from './notification-inbox-view';
import type { AppNotificationNewnessBaseline } from './notification-read-state';
import { useNotificationReadState, useNotificationReadStateUpdaters } from './use-notification-read-state';

/**
 * 받은 글 > 목록 행 (새 글 강조 포함): 화면을 연 순간의 본 번호로 기준 고정 > 받은 글은 곧바로 본 것으로 기록
 * 기준을 잡기 전(저장값 읽는 중)에는 행 없음, isBaselineReady false
 */
export function useInboxRowViews(kind: InboxNotificationKind, summaries: AppNotificationSummary[]) {
  const { readState, isLoaded: isReadStateLoaded } = useNotificationReadState();
  const { markNotificationsSeen } = useNotificationReadStateUpdaters();
  // 화면을 연 순간의 본 번호 (저장값을 다 읽은 뒤 한 번만 잡음, 처음 방문이면 seenUpToId 없음)
  const [openedBaseline, setOpenedBaseline] = useState<{ seenUpToId?: number }>();
  const loadedIdsKey = summaries.map((summary) => summary.id).join(',');

  // 저장값을 다 읽은 첫 그리기에서 한 번만 잡음 (그리는 중 상태 맞추기: 효과를 거치면 한 번 더 그림)
  if (isReadStateLoaded && openedBaseline === undefined) setOpenedBaseline({ seenUpToId: readState.seenUpToIdByKind[kind] });

  useEffect(() => {
    // 기준을 잡기 전에 본 번호를 올리면 이번에 받은 글이 새 글로 보이지 않음
    if (openedBaseline === undefined) return;
    if (loadedIdsKey.length > 0) markNotificationsSeen(kind, loadedIdsKey.split(',').map(Number));
  }, [kind, loadedIdsKey, markNotificationsSeen, openedBaseline]);

  const now = new Date();
  const baseline: AppNotificationNewnessBaseline = {
    seenUpToId: openedBaseline?.seenUpToId,
    openedNotificationIds: new Set(readState.openedNotificationIds),
  };
  const rowViews: NotificationInboxRowView[] =
    openedBaseline !== undefined ? summaries.map((summary) => toNotificationInboxRowView(summary, baseline, now)) : [];
  return { rowViews, now, isBaselineReady: openedBaseline !== undefined };
}
