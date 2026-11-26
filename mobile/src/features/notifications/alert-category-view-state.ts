import { ALERT_CATEGORIES, type AlertCategory } from '@/data/models/alert-category';
import type { PushUnavailableReason } from '@/data/sources/device-notification-client';

import { ALERT_CATEGORY_TEXTS } from './alert-category-texts';

/** 알림 종류 스위치 행 표시 내용 */
export interface AlertCategoryRowView {
  category: AlertCategory;
  title: string;
  detail: string;
  isOn: boolean;
}

// 알림을 쓸 수 없는 이유 > 안내 한 줄
const PUSH_UNAVAILABLE_MESSAGES: Record<PushUnavailableReason, string> = {
  WEB: '알림은 앱에서 받을 수 있어요.',
  NOT_PHYSICAL_DEVICE: '알림은 실제 기기에서 받을 수 있어요.',
  NO_PROJECT_ID: '이 빌드는 알림을 준비하고 있어요.',
};

/** 켠 알림 종류 > 스위치 행 3개 (표시 순서 고정) */
export function toAlertCategoryRowViews(enabledAlertCategories: AlertCategory[]): AlertCategoryRowView[] {
  return ALERT_CATEGORIES.map((category) => ({
    category,
    title: ALERT_CATEGORY_TEXTS[category].title,
    detail: ALERT_CATEGORY_TEXTS[category].detail,
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

// 알림 종류 저장 확인 문구 (알림 막대)
export const ALERT_CATEGORY_SAVED_MESSAGE = '알림 설정을 저장했어요';
