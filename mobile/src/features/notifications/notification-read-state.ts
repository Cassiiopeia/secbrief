import type { AppNotificationKind, AppNotificationSummary } from '@/data/models/app-notification';
import type { AppNotificationReadState } from '@/data/models/app-notification-read-state';

// 기기에 남기는 연 글 번호 수 한도 (넘으면 오래된 것부터 버림)
export const OPENED_NOTIFICATION_MAXIMUM_COUNT = 500;
// 알림함을 처음 열 때(본 기록 없음) 새 글로 보는 기간 (24시간)
export const FIRST_VISIT_NEW_WINDOW_MILLISECONDS = 24 * 60 * 60 * 1000;

/** 새 글 판정 기준: 화면을 연 순간의 본 글 번호(없으면 처음 방문) + 연 글 번호 */
export interface AppNotificationNewnessBaseline {
  seenUpToId?: number;
  openedNotificationIds: ReadonlySet<number>;
}

/** 연 글 하나 더하기: 이미 있으면 맨 뒤로, 한도를 넘으면 오래된 것부터 버림 */
export function toReadStateWithOpened(readState: AppNotificationReadState, notificationId: number): AppNotificationReadState {
  const nextIds = [...readState.openedNotificationIds.filter((id) => id !== notificationId), notificationId];
  return {
    ...readState,
    openedNotificationIds: nextIds.slice(Math.max(nextIds.length - OPENED_NOTIFICATION_MAXIMUM_COUNT, 0)),
  };
}

/** 목록에서 본 가장 큰 번호 반영 (작아지지 않음) */
export function toReadStateWithSeen(
  readState: AppNotificationReadState,
  kind: AppNotificationKind,
  seenNotificationIds: number[],
): AppNotificationReadState {
  if (seenNotificationIds.length === 0) return readState;
  const previousSeenUpToId = readState.seenUpToIdByKind[kind];
  const nextSeenUpToId = Math.max(previousSeenUpToId ?? 0, ...seenNotificationIds);
  if (nextSeenUpToId === previousSeenUpToId) return readState;
  return { ...readState, seenUpToIdByKind: { ...readState.seenUpToIdByKind, [kind]: nextSeenUpToId } };
}

/** 종 점: 최근 글 번호가 본 번호보다 크면 (본 기록이 없으면 글이 있기만 해도) 아직 안 본 글 있음 */
export function hasUnseenNotification(latestNotificationId: number | undefined, seenUpToId: number | undefined): boolean {
  if (latestNotificationId === undefined) return false;
  return seenUpToId === undefined || latestNotificationId > seenUpToId;
}

/**
 * 목록 한 줄이 새 글인지: 연 글이면 아님 > 처음 방문이면 24시간 안의 글 > 그 밖은 화면을 연 순간의 본 번호보다 큰 글
 */
export function isNotificationNew(summary: AppNotificationSummary, baseline: AppNotificationNewnessBaseline, now: Date): boolean {
  if (baseline.openedNotificationIds.has(summary.id)) return false;
  if (baseline.seenUpToId === undefined) {
    return now.getTime() - summary.createdAt.getTime() < FIRST_VISIT_NEW_WINDOW_MILLISECONDS;
  }
  return summary.id > baseline.seenUpToId;
}
