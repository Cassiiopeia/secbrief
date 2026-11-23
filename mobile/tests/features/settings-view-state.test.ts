/**
 * 설정: 문의 메일 주소(앱 버전 제목), 지원 페이지와 같은 주소, 출처 행 문구 3종
 */
import * as fs from 'fs';
import * as path from 'path';

import { supportLinks, toContactMailUrl } from '@/core/config/support-links';
import type { CollectSourceStatus } from '@/data/models/collect-source-status';
import { toSourceStatusRowView } from '@/features/settings/settings-view-state';

import { NOW } from './briefing-item-fixtures';

const kisaStatus: CollectSourceStatus = {
  code: 'KISA',
  displayName: 'KISA 보호나라',
  kind: 'advisory',
  region: 'domestic',
  lastCollectedAt: new Date(2026, 10, 23, 9, 12),
  isLastCollectSucceeded: true,
};
const ghsaStatus: CollectSourceStatus = {
  code: 'GHSA',
  displayName: 'GitHub 보안 권고',
  kind: 'vulnerability',
  region: 'overseas',
  lastCollectedAt: new Date(2026, 10, 23, 9, 12),
  isLastCollectSucceeded: true,
};

test('문의 메일 주소와 제목에 앱 버전', () => {
  expect(toContactMailUrl('0.4.0')).toBe(
    `mailto:${supportLinks.contactEmail}?subject=${encodeURIComponent('[시큐브리프] 문의 (앱 0.4.0)')}`,
  );
});
test('문의 주소가 지원 페이지 문구와 같다', () => {
  // 단일 원본 확인: site/support.html 에 같은 주소가 있어야 함
  const supportPage = fs.readFileSync(path.join(__dirname, '../../../site/support.html'), 'utf-8');
  expect(supportPage).toContain(supportLinks.contactEmail);
});
test('출처 행: 지역·종류·마지막 갱신', () => {
  expect(toSourceStatusRowView(kisaStatus, NOW)).toEqual({
    code: 'KISA',
    title: 'KISA 보호나라',
    detail: '국내 · 공지 · 마지막 갱신 2026.11.23 09:12 · 2시간 전',
  });
});
test('한 번도 못 모은 출처', () => {
  expect(toSourceStatusRowView({ ...ghsaStatus, lastCollectedAt: undefined }, NOW).detail).toBe('해외 · 취약점 · 아직 모으는 중이에요');
});
test('마지막 시도 실패', () => {
  expect(toSourceStatusRowView({ ...kisaStatus, isLastCollectSucceeded: false }, NOW).detail).toMatch(/다음 주기에 다시 모아요$/);
});
