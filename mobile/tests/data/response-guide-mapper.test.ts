/**
 * 대응 모아보기 응답 읽기: 기사 상세 구역(모르는 구역·이유·깨진 기사 버림, 화면 순서 정렬), 이전 서버(필드 없음), 알림 상세 기사별 요약
 * (공개 출처 형식의 예시 값, 실제 기사 아님)
 */
import { toArticleDetail } from '@/data/mappers/article-detail-mapper';
import { toAppNotificationDetail } from '@/data/mappers/notification-response-mapper';
import { toResponseGuideSections } from '@/data/mappers/response-guide-mapper';

const toArticle = (id: number, title: string, overrides: Record<string, unknown> = {}) => ({
  id,
  kind: 'ADVISORY',
  region: 'DOMESTIC',
  topic: 'VULNERABILITY',
  title,
  translatedTitle: null,
  originalUrl: `https://example.test/articles/${id}`,
  publisherName: 'KISA 보호나라',
  publishedAt: '2026-11-24T15:00:00Z',
  isPublishedAtEstimated: false,
  cveId: null,
  cvssScore: null,
  cvssVersion: null,
  isKev: false,
  isUrgent: false,
  sourceCode: 'KISA',
  oneLine: null,
  ...overrides,
});

const KISA_NOTICE = toArticle(725, '예시 제품 보안 업데이트 권고');
const CVE_ENTRY = toArticle(12, 'CVE-2026-1111: Example Gateway authentication bypass', {
  kind: 'VULNERABILITY',
  region: 'OVERSEAS',
  publisherName: 'NVD',
  sourceCode: 'CISA_KEV',
  cveId: 'CVE-2026-1111',
  cvssScore: 9.8,
  cvssVersion: '3.1',
  isKev: true,
});
const NEWS = toArticle(576, 'Example Gateway hit by another critical flaw', {
  kind: 'NEWS',
  region: 'OVERSEAS',
  publisherName: 'The Hacker News',
  sourceCode: 'THE_HACKER_NEWS',
  translatedTitle: '예시 게이트웨이에 또 치명적 결함',
  oneLine: '인증 없이 관리자 권한을 얻는 결함이 또 나왔어요.',
});

describe('기사 상세 대응 모아보기', () => {
  test('구역을 화면 순서로 다시 정렬하고 이유·겹친 CVE·조치 기한을 읽는다', () => {
    const sections = toResponseGuideSections([
      { section: 'NEWS', totalCount: 6, items: [{ article: NEWS, sourceName: 'The Hacker News', matchReason: 'SAME_PRODUCT' }] },
      {
        section: 'FACT',
        totalCount: 1,
        items: [{ article: CVE_ENTRY, sourceName: 'CISA KEV', matchReason: 'SAME_CVE', matchedCveId: 'CVE-2026-1111', actionDueDate: '2026-12-11' }],
      },
      { section: 'OFFICIAL', totalCount: 1, items: [{ article: KISA_NOTICE, sourceName: 'KISA 보호나라', matchReason: 'SAME_VENDOR', matchedCveId: null }] },
    ]);

    expect(sections.map((section) => section.section)).toEqual(['OFFICIAL', 'FACT', 'NEWS']);
    expect(sections[0].items[0]).toMatchObject({ sourceName: 'KISA 보호나라', matchReason: 'SAME_VENDOR', matchedCveId: undefined });
    expect(sections[1].items[0]).toMatchObject({ matchedCveId: 'CVE-2026-1111', actionDueDate: '2026-12-11' });
    expect(sections[1].items[0].article).toMatchObject({ id: '12', isKev: true, cvssScore: 9.8 });
    expect(sections[2]).toMatchObject({ totalCount: 6 });
    expect(sections[2].items[0].article.oneLine).toBe('인증 없이 관리자 권한을 얻는 결함이 또 나왔어요.');
  });

  test('모르는 구역·이유, 깨진 기사, 항목이 없는 구역은 버리고 전체 건수는 보인 수보다 작지 않게', () => {
    const sections = toResponseGuideSections([
      { section: 'RUMOR', totalCount: 1, items: [{ article: NEWS, sourceName: 'x', matchReason: 'SAME_CVE' }] },
      { section: 'NEWS', totalCount: 1, items: [{ article: NEWS, sourceName: 'x', matchReason: 'GUESS' }] },
      { section: 'FACT', totalCount: 0, items: [] },
      {
        section: 'OFFICIAL',
        totalCount: 0,
        items: [{ article: { id: 'x' }, matchReason: 'SAME_CVE' }, { article: KISA_NOTICE, sourceName: '', matchReason: 'SAME_PRODUCT' }],
      },
      'broken',
    ]);

    expect(sections).toHaveLength(1);
    expect(sections[0]).toMatchObject({ section: 'OFFICIAL', totalCount: 1 });
    // 출처 이름이 비면 게시자 이름으로
    expect(sections[0].items[0].sourceName).toBe('KISA 보호나라');
  });

  test('이전 서버(필드 없음)·형식이 틀린 값은 빈 배열이고 함께 볼 소식은 그대로 읽는다', () => {
    const detail = toArticleDetail({ article: CVE_ENTRY as never, relatedItems: [NEWS as never] });

    expect(detail.responseGuideSections).toEqual([]);
    expect(detail.relatedItems.map((item) => item.id)).toEqual(['576']);
    expect(toResponseGuideSections({ sections: [] })).toEqual([]);
  });
});

describe('알림 상세 기사별 대응 안내 요약', () => {
  const ALERT = {
    id: 1,
    kind: 'ALERT',
    alertCategory: 'KEV',
    alertCategories: ['KEV'],
    title: '긴급 보안 소식이 들어왔어요',
    createdAt: '2026-11-27T00:51:00+09:00',
    articleSections: [{ alertCategory: 'KEV', articles: [CVE_ENTRY] }],
  };

  test('대응·건수·첫 공식 대응을 읽고, 빈 대응 글과 번호 없는 요약은 버린다', () => {
    const detail = toAppNotificationDetail({
      ...ALERT,
      articleGuides: [
        {
          articleId: 12,
          actions: ['3.2.1로 올리세요.', ' ', 7],
          officialCount: 1,
          factCount: 0,
          newsCount: 4,
          leadOfficialItem: { article: KISA_NOTICE, sourceName: 'KISA 보호나라', matchReason: 'SAME_VENDOR' },
        },
        { actions: [], officialCount: 3 },
      ],
    });

    expect(detail?.articleGuides).toHaveLength(1);
    expect(detail?.articleGuides[0]).toMatchObject({ articleId: '12', actions: ['3.2.1로 올리세요.'], officialCount: 1, factCount: 0, newsCount: 4 });
    expect(detail?.articleGuides[0].leadOfficialItem?.article.id).toBe('725');
  });

  test('이전 서버(필드 없음)는 빈 배열, 건수가 숫자가 아니면 0, 첫 공식 대응이 깨졌으면 없음', () => {
    expect(toAppNotificationDetail(ALERT)?.articleGuides).toEqual([]);
    const detail = toAppNotificationDetail({
      ...ALERT,
      articleGuides: [{ articleId: 12, officialCount: '2', factCount: -1, leadOfficialItem: { article: null } }],
    });
    expect(detail?.articleGuides[0]).toMatchObject({ actions: [], officialCount: 0, factCount: 0, newsCount: 0, leadOfficialItem: undefined });
  });
});
