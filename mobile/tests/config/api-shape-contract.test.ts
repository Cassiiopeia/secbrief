/**
 * 서버 <-> 앱 API 모양 계약 (#93): 서버 테스트가 실제 응답과 키를 맞춰 보는 JSON 예시를 앱 매퍼로 읽어 봄
 * 예시 원본: server/SB-Web/src/test/resources/contract/*.json (값이 아니라 칸 이름·모양이 계약)
 */
import * as fs from 'fs';
import * as path from 'path';

import { toTagCatalog } from '@/data/mappers/catalog-tag-mapper';
import { toNewArticleCounts } from '@/data/mappers/new-article-counts-mapper';
import { toAppNotificationDetail } from '@/data/mappers/notification-response-mapper';
import { toAlertEstimate } from '@/data/models/alert-estimate';

const CONTRACT_ROOT = path.join(__dirname, '../../../server/SB-Web/src/test/resources/contract');
const readContract = (fileName: string): unknown => JSON.parse(fs.readFileSync(path.join(CONTRACT_ROOT, fileName), 'utf-8'));

test('카탈로그 GET /api/v1/catalog/tags: 버전 + 항목 (kind·code·이름·짧은 이름·묶음·분야·순서·널리 씀)', () => {
  const catalog = toTagCatalog(readContract('catalog-tags.json'));
  expect(catalog?.version.length).toBeGreaterThan(0);
  expect(catalog?.tags.length).toBeGreaterThan(0);
  const product = catalog?.tags.find((tag) => tag.kind === 'PRODUCT');
  expect(product).toEqual(
    expect.objectContaining({ code: expect.any(String), name: expect.any(String), shortName: expect.any(String), isWidelyUsed: expect.any(Boolean) }),
  );
  expect(product?.groupCode).toEqual(expect.any(String));
  expect(product?.fieldCode).toEqual(expect.any(String));
});

test('오늘 새 소식 GET /api/v1/articles/new-counts: 기준 시각, 건수 4개, 분야·생태계·출처별', () => {
  const counts = toNewArticleCounts(readContract('new-counts.json'));
  expect(counts).toBeDefined();
  expect(counts?.lastCollectedAt).toBeInstanceOf(Date);
  expect(counts?.fieldCounts[0]).toEqual({ code: expect.any(String), count: expect.any(Number), sampleNames: expect.any(Array) });
  expect(counts?.ecosystemCounts.length).toBeGreaterThan(0);
  expect(Object.keys(counts?.countBySourceCode ?? {}).length).toBeGreaterThan(0);
});

test('알림 추정 POST /api/v1/push-devices/alert-estimate: 날짜 + 횟수 5개', () => {
  const estimate = toAlertEstimate(readContract('alert-estimate.json'));
  expect(estimate?.date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  expect(estimate?.currentPushCount).toBeGreaterThan(0);
  expect(estimate?.immediatePushCount).toBeGreaterThan(0);
});

test('아침 요약 상세 GET /api/v1/notifications/{id} (DIGEST): 담긴 수, 꼭 볼 것, 분야 묶음 (빈 묶음은 버림)', () => {
  const detail = toAppNotificationDetail(readContract('digest-notification.json') as object);
  expect(detail?.kind).toBe('DIGEST');
  expect(detail?.alertCategory).toBe('INTEREST');
  expect(detail?.digest?.notableArticles[0]).toEqual(expect.objectContaining({ isNotable: true, fieldCode: expect.any(String) }));
  expect(detail?.digest?.fieldSections.map((section) => section.fieldCode)).toEqual(['web-app']);
  expect(detail?.digest?.fieldSections[0].articles[0]).toEqual(
    expect.objectContaining({ ecosystemCode: expect.any(String), packageName: expect.any(String) }),
  );
});
