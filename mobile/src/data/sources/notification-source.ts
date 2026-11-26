import { AppFailure, INVALID_RESPONSE_ERROR_CODE } from '@/core/error/app-failure';

import {
  type AppNotificationDetailApiResponse,
  type AppNotificationPageApiResponse,
  type LatestAppNotificationIdsApiResponse,
  toAppNotificationDetail,
  toAppNotificationPage,
  toLatestAppNotificationIds,
} from '../mappers/notification-response-mapper';
import type {
  AppNotificationDetail,
  AppNotificationKind,
  AppNotificationPage,
  LatestAppNotificationIds,
} from '../models/app-notification';
import { fetchApiData } from './secbrief-api-client';

/** 알림함 한 페이지 조회: GET /api/v1/notifications?kind= (cursor 는 다음 페이지 위치) */
export async function fetchAppNotificationPage(kind: AppNotificationKind, cursor?: string): Promise<AppNotificationPage> {
  const response = await fetchApiData<AppNotificationPageApiResponse>('/api/v1/notifications', { kind, cursor });
  return toAppNotificationPage(response);
}

/** 종류별 최근 글 번호 조회: GET /api/v1/notifications/latest */
export async function fetchLatestAppNotificationIds(): Promise<LatestAppNotificationIds> {
  return toLatestAppNotificationIds(await fetchApiData<LatestAppNotificationIdsApiResponse>('/api/v1/notifications/latest'));
}

/** 알림함 상세 조회: GET /api/v1/notifications/{id} (형식이 틀린 응답은 APP_INVALID_RESPONSE) */
export async function fetchAppNotificationDetail(notificationId: string): Promise<AppNotificationDetail> {
  const response = await fetchApiData<AppNotificationDetailApiResponse>(
    `/api/v1/notifications/${encodeURIComponent(notificationId)}`,
  );
  const detail = toAppNotificationDetail(response);
  if (detail === undefined) throw new AppFailure(INVALID_RESPONSE_ERROR_CODE, '서버 응답을 읽지 못했어요.');
  return detail;
}
