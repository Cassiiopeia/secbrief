/**
 * 알림 설정 뷰 상태: 스위치 행 3개, 스위치 바꾼 뒤 목록, 쓸 수 없는 이유 문구 (알림 누름 경로는 notification-target-route.test)
 */
import { toAlertCategories } from '@/data/models/alert-category';
import {
  toAlertCategoryRowViews,
  toPushUnavailableMessage,
  toToggledAlertCategories,
} from '@/features/notifications/alert-category-view-state';

test('스위치 행은 종류 3개를 고정 순서로 보여 준다', () => {
  expect(toAlertCategoryRowViews(['KEV'])).toEqual([
    { category: 'KISA_URGENT', title: 'KISA 긴급 공지', detail: 'KISA 보호나라가 긴급으로 올린 공지', isOn: false },
    { category: 'KEV', title: '악용 중인 취약점', detail: 'CISA KEV 에 오른 취약점', isOn: true },
    { category: 'CRITICAL_CVE', title: '고위험 취약점', detail: 'CVSS 9.0 이상', isOn: false },
  ]);
});

test('스위치를 켜면 표시 순서로 더한다', () => {
  expect(toToggledAlertCategories(['CRITICAL_CVE'], 'KISA_URGENT', true)).toEqual(['KISA_URGENT', 'CRITICAL_CVE']);
});
test('이미 켠 종류를 다시 켜도 중복이 없다', () => {
  expect(toToggledAlertCategories(['KEV'], 'KEV', true)).toEqual(['KEV']);
});
test('마지막 스위치를 끄면 빈 목록 (서버가 기기를 지움)', () => {
  expect(toToggledAlertCategories(['KEV'], 'KEV', false)).toEqual([]);
});

test('쓸 수 없는 이유마다 해요체 안내', () => {
  expect(toPushUnavailableMessage('WEB')).toBe('알림은 앱에서 받을 수 있어요.');
  expect(toPushUnavailableMessage('NOT_PHYSICAL_DEVICE')).toBe('알림은 실제 기기에서 받을 수 있어요.');
  expect(toPushUnavailableMessage('NO_PROJECT_ID')).toBe('이 빌드는 알림을 준비하고 있어요.');
});

test('저장값·서버 응답에서 모르는 종류와 중복을 뺀다', () => {
  expect(toAlertCategories(['CRITICAL_CVE', 'NEW_KIND', 'KEV', 'KEV'])).toEqual(['KEV', 'CRITICAL_CVE']);
  expect(toAlertCategories('KEV')).toEqual([]);
});
