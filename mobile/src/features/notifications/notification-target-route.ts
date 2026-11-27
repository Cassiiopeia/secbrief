import type { AppNotificationKind } from '@/data/models/app-notification';

import {
  APP_ROUTES,
  type ArticleDetailRoute,
  type NoticeDetailRoute,
  type NotificationDetailRoute,
  type NotificationInboxRoute,
  toArticleDetailRoute,
  toNoticeDetailRoute,
  toNotificationDetailRoute,
} from '../navigation/app-routes';

// 알림을 눌렀을 때 열 탭 경로 (이전 서버·모르는 알림)
export const TODAY_TAB_ROUTE = APP_ROUTES.home;
export const VULNERABILITY_TAB_ROUTE = APP_ROUTES.vulnerability;
export type NotificationTargetRoute =
  | typeof TODAY_TAB_ROUTE
  | typeof VULNERABILITY_TAB_ROUTE
  | ArticleDetailRoute
  | NotificationDetailRoute
  | NoticeDetailRoute
  | NotificationInboxRoute;

/** 눌렀을 때 열 곳 + 알림함 글 번호 (연 글로 표시할 번호, 없으면 없음) */
export interface NotificationTarget {
  route: NotificationTargetRoute;
  notificationId?: number;
}

// 서버 푸시 data.notificationKind 값 4개: 긴급 알림, 공지사항, 아침 요약(DIGEST, 알림 상세가 요약으로 그림), 방해 금지 시간이 끝난 뒤 보내는 모아 보기
const NOTICE_NOTIFICATION_KIND = 'NOTICE';
const QUIET_HOURS_DIGEST_NOTIFICATION_KIND = 'QUIET_HOURS_DIGEST';
// 서버 푸시 data.targetTab 값: 취약점 탭 (이전 서버, 그 밖은 홈)
const VULNERABILITY_TARGET_TAB = 'vulnerability';
// 서버 푸시 data.articleId·notificationId 모양: 숫자만
const NUMERIC_ID_PATTERN = /^\d+$/;

/**
 * 알림 data > 열 곳 (앞에서부터 처음 맞는 것)
 * 숫자 articleId 기사 상세 > NOTICE 공지 상세 > 숫자 notificationId 알림 상세 > QUIET_HOURS_DIGEST 알림함 > targetTab(vulnerability 취약점, 그 밖 홈)
 */
export function toNotificationTarget(notificationData: unknown): NotificationTarget {
  if (typeof notificationData !== 'object' || notificationData === null) return { route: TODAY_TAB_ROUTE };
  const { articleId, notificationId, notificationKind, targetTab } = notificationData as Record<string, unknown>;
  const numericNotificationId = toNumericId(notificationId);
  const openedNotificationId = numericNotificationId === undefined ? undefined : Number(numericNotificationId);

  const numericArticleId = toNumericId(articleId);
  if (numericArticleId !== undefined) return { route: toArticleDetailRoute(numericArticleId), notificationId: openedNotificationId };
  if (numericNotificationId !== undefined) {
    return {
      route:
        notificationKind === NOTICE_NOTIFICATION_KIND
          ? toNoticeDetailRoute(numericNotificationId)
          : toNotificationDetailRoute(numericNotificationId),
      notificationId: openedNotificationId,
    };
  }
  if (notificationKind === QUIET_HOURS_DIGEST_NOTIFICATION_KIND) return { route: APP_ROUTES.notifications };
  return { route: targetTab === VULNERABILITY_TARGET_TAB ? VULNERABILITY_TAB_ROUTE : TODAY_TAB_ROUTE };
}

/** 알림 data > 열 경로 (toNotificationTarget 의 경로만) */
export function toNotificationTargetRoute(notificationData: unknown): NotificationTargetRoute {
  return toNotificationTarget(notificationData).route;
}

/** 열 경로가 하단 탭인지 (탭은 바꾸기, 그 밖은 쌓기) */
export function isTabRoute(route: NotificationTargetRoute): boolean {
  return route === TODAY_TAB_ROUTE || route === VULNERABILITY_TAB_ROUTE;
}

/**
 * 상세 경로 종류 <-> 받은 글 종류가 다르면 맞는 상세 경로, 맞으면 undefined (#87)
 * 공지 경로(notices)는 NOTICE 만, 알림 경로(notifications)는 ALERT·DIGEST (아침 요약은 알림 경로로 열림)
 */
export function toDetailRouteForReceivedKind(
  routeKind: AppNotificationKind,
  receivedKind: AppNotificationKind,
  notificationId: string,
): NoticeDetailRoute | NotificationDetailRoute | undefined {
  const isNoticeRoute = routeKind === 'NOTICE';
  const isNoticeReceived = receivedKind === 'NOTICE';
  if (isNoticeRoute === isNoticeReceived) return undefined;
  return isNoticeReceived ? toNoticeDetailRoute(notificationId) : toNotificationDetailRoute(notificationId);
}

function toNumericId(value: unknown): string | undefined {
  return typeof value === 'string' && NUMERIC_ID_PATTERN.test(value) ? value : undefined;
}
