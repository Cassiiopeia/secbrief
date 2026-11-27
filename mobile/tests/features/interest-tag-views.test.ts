/**
 * #93 분류 태그·관심 기술 뷰: 카탈로그 매퍼(모르는 kind 버림), 태그 배지(모르는 code 숨김), 취약점 칩 +N, 경로 조건 왕복,
 * 관심 기술 요약·검색·묶음, 오늘 새 소식 띠, 아침 요약 묶음 (값은 가짜 카탈로그: 실제 목록은 서버 DB)
 */
import { toTagCatalog } from '@/data/mappers/catalog-tag-mapper';
import type { AppNotificationDetail } from '@/data/models/app-notification';
import type { BriefingItem } from '@/data/models/briefing-item';
import type { NewArticleCounts } from '@/data/models/new-article-counts';
import { toTagBadges, toTagCatalogIndex } from '@/features/catalog/tag-catalog-view';
import {
  searchProducts,
  toEstimateLine,
  toInterestSummaryLines,
  toPickedProductsOutsideGroups,
  toProductGroupSections,
  toToggledInterests,
} from '@/features/interests/interest-view-state';
import { toDigestDetailView } from '@/features/notifications/digest-detail-view';
import { toTodayNewArticlesView } from '@/features/today/today-new-articles-view';
import { toSourceGroupChipOptions, toTagChipOptions } from '@/features/vulnerability/vulnerability-chip-view';
import {
  DEFAULT_VULNERABILITY_FEED_SELECTION,
  toSelectionFromRouteParameters,
  toVulnerabilityRouteParameters,
} from '@/features/vulnerability/vulnerability-feed-selection';

import { NOW, nvdItem } from './briefing-item-fixtures';

const catalog = toTagCatalog({
  version: 'v1',
  tags: [
    { kind: 'ECOSYSTEM', code: 'eco-b', name: '생태계 B', shortName: 'B', displayOrder: 20 },
    { kind: 'ECOSYSTEM', code: 'eco-a', name: '생태계 A', shortName: 'A', displayOrder: 10 },
    { kind: 'FIELD', code: 'field-a', name: '분야 A 전체 이름', shortName: '분야 A', displayOrder: 10 },
    { kind: 'PRODUCT_GROUP', code: 'group-a', name: '묶음 A', displayOrder: 10 },
    { kind: 'PRODUCT', code: 'product-a', name: '제품 A', groupCode: 'group-a', fieldCode: 'field-a', displayOrder: 10, isWidelyUsed: true },
    { kind: 'PRODUCT', code: 'product-search', name: 'Search Only', displayOrder: 20, isWidelyUsed: false },
    { kind: 'NEW_KIND', code: 'x', name: '새 종류' },
    { kind: 'FIELD', code: '', name: '빈 code' },
  ],
});
const index = toTagCatalogIndex(catalog!);
const taggedItem: BriefingItem = { ...nvdItem, fieldCode: 'field-a', ecosystemCode: 'eco-a', productCode: 'product-a', isNotable: true };

describe('카탈로그', () => {
  test('모르는 kind·빈 code 는 버리고 kind > 순서로 정렬, 짧은 이름이 없으면 이름', () => {
    expect(catalog?.tags.map((tag) => tag.code)).toEqual(['field-a', 'eco-a', 'eco-b', 'group-a', 'product-a', 'product-search']);
    expect(index.productGroups[0].shortName).toBe('묶음 A');
  });
  test('공백만 있는 글은 없는 값으로 보고 앞뒤 빈칸은 정리 (#85)', () => {
    const blankCatalog = toTagCatalog({
      version: 'v1',
      tags: [
        { kind: 'FIELD', code: '   ', name: '공백 code' },
        { kind: 'PRODUCT', code: ' product-b ', name: ' 제품 B ', shortName: '  ', groupCode: ' ', fieldCode: '\t' },
      ],
    });
    expect(blankCatalog?.tags).toEqual([
      { kind: 'PRODUCT', code: 'product-b', name: '제품 B', shortName: '제품 B', groupCode: undefined, fieldCode: undefined, displayOrder: 0, isWidelyUsed: false },
    ]);
  });
  test('태그 배지: 주요 이유(널리 쓰는 제품) > 생태계 > 분야, 모르는 code 는 그리지 않음', () => {
    expect(toTagBadges(taggedItem, index, true)).toEqual([
      { label: '널리 씀', tone: 'notice' },
      { label: 'A', tone: 'neutral' },
      { label: '분야 A', tone: 'neutral' },
    ]);
    expect(toTagBadges({ ...nvdItem, fieldCode: 'removed-field', isNotable: false, subjectName: 'Fortinet' }, index, true)).toEqual([
      { label: 'Fortinet', tone: 'subject' },
    ]);
  });
  test('CVSS 게이지가 옆에 있으면 "CVSS 9.0 이상" 을 다시 쓰지 않음 (AI 중요만)', () => {
    const criticalItem: BriefingItem = { ...nvdItem, productCode: undefined, fieldCode: 'field-a', cvssScore: 9.8, isNotable: true };
    expect(toTagBadges(criticalItem, index, true)[0]).toEqual({ label: 'AI 중요', tone: 'notice' });
    expect(toTagBadges(criticalItem, index, false)[0]).toEqual({ label: 'CVSS 9.0 이상 · AI 중요', tone: 'notice' });
  });
});

const counts: NewArticleCounts = {
  since: new Date('2026-11-26T15:00:00Z'),
  lastCollectedAt: new Date('2026-11-26T22:36:00Z'),
  totalCount: 39,
  exploitedCount: 1,
  kisaCount: 0,
  newsCount: 8,
  fieldCounts: [{ code: 'field-a', count: 11, sampleNames: ['Budibase', 'ToolJet'] }, { code: 'removed-field', count: 3, sampleNames: [] }],
  ecosystemCounts: [{ code: 'eco-a', count: 5, sampleNames: ['appium'] }],
  countBySourceCode: { GHSA: 2, GHSA_HIGH: 3, NVD: 12 },
};

describe('취약점 탭 칩', () => {
  test('출처 묶음: 관심 기술을 고르면 내 기술 N 이 맨 앞, 모든 출처 +N 은 뉴스 뺀 오늘 소식, 묶음 +N 은 출처 합', () => {
    const options = toSourceGroupChipOptions(6, counts);
    expect(options.map((option) => [option.value, option.countLabel])).toEqual([
      ['myTech', '6'],
      ['all', '+31'],
      ['kisa', undefined],
      ['openSource', '+5'],
      ['linux', undefined],
      ['runtime', undefined],
    ]);
    expect(toSourceGroupChipOptions(0, undefined)[0]).toEqual({ value: 'all', label: '모든 출처', countLabel: undefined });
  });
  test('둘째 줄: 오픈소스는 카탈로그 생태계 순서, 모든 출처는 분야, 카탈로그가 비면 없음', () => {
    const openSource = { ...DEFAULT_VULNERABILITY_FEED_SELECTION, sourceGroup: 'openSource' as const };
    expect(toTagChipOptions(openSource, index, counts).map((option) => `${option.label}${option.countLabel ?? ''}`)).toEqual([
      '전체 생태계+5',
      'A+5',
      'B',
    ]);
    expect(toTagChipOptions(DEFAULT_VULNERABILITY_FEED_SELECTION, index, counts)[1]).toEqual({ value: 'field-a', label: '분야 A', countLabel: '+11' });
    expect(toTagChipOptions(DEFAULT_VULNERABILITY_FEED_SELECTION, toTagCatalogIndex({ version: '', tags: [] }), counts)).toEqual([]);
  });
  test('경로 조건 왕복: 홈 띠 > 취약점 탭 (분야 + 오늘), 조건이 없으면 지금 고른 것 유지', () => {
    const parameters = toVulnerabilityRouteParameters({ sourceGroup: 'all', segment: 'all', fieldCode: 'field-a', isTodayOnly: true });
    expect(toSelectionFromRouteParameters(parameters)).toEqual({
      segment: 'all',
      sourceGroup: 'all',
      fieldCode: 'field-a',
      ecosystemCode: undefined,
      isTodayOnly: true,
    });
    expect(toSelectionFromRouteParameters({})).toBeUndefined();
  });
});

describe('관심 기술', () => {
  const interests = { productCodes: ['product-a', 'product-search', 'removed-product'], fieldCodes: [], ecosystemCodes: ['eco-b'] };
  test('요약 3줄: 지워진 code 는 빼고 이름, 안 골랐으면 안내', () => {
    expect(toInterestSummaryLines(interests, index)).toEqual(['제품 제품 A, Search Only', '분야 아직 안 골랐어요', '생태계 B']);
  });
  test('넣기·빼기, 인기 묶음, 검색으로 고른 것, 검색(대소문자 무시)', () => {
    expect(toToggledInterests(interests, 'field', 'field-a').fieldCodes).toEqual(['field-a']);
    expect(toToggledInterests(interests, 'product', 'product-a').productCodes).toEqual(['product-search', 'removed-product']);
    expect(toProductGroupSections(index)).toEqual([{ groupCode: 'group-a', title: '묶음 A', options: [{ value: 'product-a', label: '제품 A' }] }]);
    expect(toPickedProductsOutsideGroups(interests, index)).toEqual([{ value: 'product-search', label: 'Search Only' }]);
    expect(searchProducts(index, 'search')).toEqual([{ value: 'product-search', label: 'Search Only' }]);
  });
  test('아래 줄: 고른 수 + 어제 기준 알림 (추정 전이면 고른 수만)', () => {
    expect(toEstimateLine(interests, index, undefined)).toBe('제품 2 · 분야 0 · 생태계 1');
    const estimate = { date: '2026-11-26', immediatePushCount: 1, immediateArticleCount: 2, digestArticleCount: 6, digestPushCount: 1, currentPushCount: 7 };
    expect(toEstimateLine(interests, index, estimate)).toBe('제품 2 · 분야 0 · 생태계 1 · 어제 기준 알림 1번 + 아침 요약 1번 (지금은 7번)');
  });
});

describe('오늘 새 소식 띠', () => {
  test('건수 있는 타일만 (악용 중 먼저), 0건인 KISA 는 끝 한 줄, 분야·생태계는 카탈로그에 있는 것만, 누르면 취약점 탭 + 오늘', () => {
    const view = toTodayNewArticlesView(counts, index);
    expect(view.tiles.map((tile) => [tile.title, tile.valueLabel, tile.tone])).toEqual([
      ['악용 중', '+1', 'feature'],
      ['분야 A', '+11', 'neutral'],
      ['A', '+5', 'neutral'],
      ['기사', '+8', 'neutral'],
    ]);
    expect(view.emptyNote).toBe('KISA 공지 새 소식 없음');
    expect(view.tiles[1].detail).toBe('Budibase, ToolJet');
    expect(view.tiles[1].href).toEqual({ pathname: '/vulnerability', params: expect.objectContaining({ fieldCode: 'field-a', today: '1' }) });
  });

  test('악용 중·KISA 가 둘 다 0건이면 타일 없이 한 줄로 묶음', () => {
    const view = toTodayNewArticlesView({ ...counts, exploitedCount: 0, kisaCount: 0 }, index);
    expect(view.tiles[0].title).toBe('분야 A');
    expect(view.emptyNote).toBe('악용 중·KISA 공지 새 소식 없음');
  });
});

describe('아침 요약', () => {
  test('분야 이름은 카탈로그, 대표 이름 2개 + 외, 모르는 분야는 분류 전', () => {
    const detail = {
      id: 51,
      kind: 'DIGEST',
      alertCategories: ['INTEREST'],
      title: '오늘 아침 요약',
      body: '',
      articleCount: 3,
      isPinned: false,
      createdAt: NOW,
      updatedAt: NOW,
      articleSections: [],
      articleGuides: [],
      digest: {
        articleCount: 3,
        notableArticles: [taggedItem],
        fieldSections: [
          {
            fieldCode: 'field-a',
            articles: [
              { ...nvdItem, id: '1', packageName: 'pkg-one' },
              { ...nvdItem, id: '2', packageName: 'pkg-two' },
              { ...nvdItem, id: '3', packageName: 'pkg-three' },
            ],
          },
          { fieldCode: 'removed-field', articles: [{ ...nvdItem, id: '4', packageName: undefined, subjectName: undefined }] },
        ],
      },
    } satisfies AppNotificationDetail;
    const view = toDigestDetailView(detail, index, NOW);
    expect(view.articleCountLabel).toBe('3건');
    expect(view.fieldSections.map((section) => [section.title, section.detail, section.countLabel])).toEqual([
      ['분야 A 전체 이름', 'pkg-one, pkg-two 외', '3건'],
      ['분류 전', undefined, '1건'],
    ]);
  });
});
