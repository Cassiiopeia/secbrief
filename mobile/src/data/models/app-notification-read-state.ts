import { APP_NOTIFICATION_KINDS, type AppNotificationKind } from './app-notification';

/**
 * 알림함 읽음 기록 (기기에만 저장, 서버로 보내지 않음)
 * seenUpToIdByKind: 종류별로 목록에서 본 가장 큰 글 번호 (종 점·새 글 표시 기준), openedNotificationIds: 연 글 번호 (최근이 뒤)
 */
export interface AppNotificationReadState {
  seenUpToIdByKind: Partial<Record<AppNotificationKind, number>>;
  openedNotificationIds: number[];
}

export const EMPTY_NOTIFICATION_READ_STATE: AppNotificationReadState = { seenUpToIdByKind: {}, openedNotificationIds: [] };

/** 임의의 값 > 읽음 기록 (숫자가 아닌 번호, 모르는 종류는 버림) */
export function toNotificationReadState(value: unknown): AppNotificationReadState {
  if (typeof value !== 'object' || value === null) return EMPTY_NOTIFICATION_READ_STATE;
  const { seenUpToIdByKind, openedNotificationIds } = value as Record<string, unknown>;
  const seenUpToIds = typeof seenUpToIdByKind === 'object' && seenUpToIdByKind !== null ? (seenUpToIdByKind as Record<string, unknown>) : {};
  const nextSeenUpToIdByKind: Partial<Record<AppNotificationKind, number>> = {};
  for (const kind of APP_NOTIFICATION_KINDS) {
    const seenUpToId = seenUpToIds[kind];
    if (typeof seenUpToId === 'number' && Number.isInteger(seenUpToId)) nextSeenUpToIdByKind[kind] = seenUpToId;
  }
  return {
    seenUpToIdByKind: nextSeenUpToIdByKind,
    openedNotificationIds: Array.isArray(openedNotificationIds)
      ? openedNotificationIds.filter((id): id is number => typeof id === 'number' && Number.isInteger(id))
      : [],
  };
}
