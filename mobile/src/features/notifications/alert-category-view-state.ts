import { ALERT_CATEGORIES, type AlertCategory } from '@/data/models/alert-category';
import type { PushUnavailableReason } from '@/data/sources/device-notification-client';

import { APP_ROUTES } from '../navigation/app-routes';

/** 알림 종류 스위치 행 표시 내용 */
export interface AlertCategoryRowView {
  category: AlertCategory;
  title: string;
  detail: string;
  isOn: boolean;
}

// 알림 종류 > 표시 이름·설명
const ALERT_CATEGORY_TEXTS: Record<AlertCategory, { title: string; detail: string }> = {
  KISA_URGENT: { title: 'KISA 긴급 공지', detail: 'KISA 보호나라가 긴급으로 올린 공지' },
  KEV: { title: '악용 중인 취약점', detail: 'CISA KEV 에 오른 취약점' },
  CRITICAL_CVE: { title: '고위험 취약점', detail: 'CVSS 9.0 이상' },
};

// 알림을 쓸 수 없는 이유 > 안내 한 줄
const PUSH_UNAVAILABLE_MESSAGES: Record<PushUnavailableReason, string> = {
  WEB: '알림은 앱에서 받을 수 있어요.',
  NOT_PHYSICAL_DEVICE: '알림은 실제 기기에서 받을 수 있어요.',
  NO_PROJECT_ID: '이 빌드는 알림을 준비하고 있어요.',
};

// 알림을 눌렀을 때 열 탭 경로
export const TODAY_TAB_ROUTE = APP_ROUTES.home;
export const VULNERABILITY_TAB_ROUTE = APP_ROUTES.vulnerability;
// 기사 상세 경로 (app/articles/[articleId] 와 같아야 함)
export type ArticleDetailRoute = `/articles/${string}`;
export type NotificationTargetRoute = typeof TODAY_TAB_ROUTE | typeof VULNERABILITY_TAB_ROUTE | ArticleDetailRoute;

// 서버 푸시 data.targetTab 값: 취약점 탭
const VULNERABILITY_TARGET_TAB = 'vulnerability';
// 서버 푸시 data.articleId 모양: 숫자만 (소식 1건을 알린 알림에만 있음)
const ARTICLE_ID_PATTERN = /^\d+$/;

/** 켠 알림 종류 > 스위치 행 3개 (표시 순서 고정) */
export function toAlertCategoryRowViews(enabledAlertCategories: AlertCategory[]): AlertCategoryRowView[] {
  return ALERT_CATEGORIES.map((category) => ({
    category,
    ...ALERT_CATEGORY_TEXTS[category],
    isOn: enabledAlertCategories.includes(category),
  }));
}

/** 스위치 하나를 바꾼 뒤의 켠 종류 전체 (표시 순서, 중복 없음) */
export function toToggledAlertCategories(
  enabledAlertCategories: AlertCategory[],
  toggledCategory: AlertCategory,
  isOn: boolean,
): AlertCategory[] {
  return ALERT_CATEGORIES.filter((category) =>
    category === toggledCategory ? isOn : enabledAlertCategories.includes(category),
  );
}

/** 알림을 쓸 수 없는 이유 > 안내 문구 */
export function toPushUnavailableMessage(reason: PushUnavailableReason): string {
  return PUSH_UNAVAILABLE_MESSAGES[reason];
}

/**
 * 알림 data > 열 경로: articleId(숫자)가 있으면 그 기사 상세, 없으면 탭
 * (targetTab 이 vulnerability 면 취약점, 그 밖·없음·모르는 값은 홈)
 */
export function toNotificationTargetRoute(notificationData: unknown): NotificationTargetRoute {
  if (typeof notificationData !== 'object' || notificationData === null) return TODAY_TAB_ROUTE;
  const { articleId, targetTab } = notificationData as Record<string, unknown>;
  if (typeof articleId === 'string' && ARTICLE_ID_PATTERN.test(articleId)) return `/articles/${articleId}`;
  return targetTab === VULNERABILITY_TARGET_TAB ? VULNERABILITY_TAB_ROUTE : TODAY_TAB_ROUTE;
}
