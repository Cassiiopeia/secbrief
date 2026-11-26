/**
 * 알림함 응답 읽기: 빠진 칸·모르는 값에 견딤 (목록, 상세 기사 묶음, 최근 번호, 공지 링크)
 */
import {
  toAppNotificationDetail,
  toAppNotificationPage,
  toAppNotificationSummary,
  toLatestAppNotificationIds,
} from '@/data/mappers/notification-response-mapper';

const ARTICLE = {
  id: 501,
  kind: 'VULNERABILITY',
  region: 'OVERSEAS',
  topic: 'VULNERABILITY',
  title: 'CVE-2026-1001: Example flaw',
  translatedTitle: '예시 제품 원격 코드 실행 취약점',
  originalUrl: 'https://nvd.nist.gov/vuln/detail/CVE-2026-1001',
  publisherName: 'NVD',
  publishedAt: '2026-11-27T00:10:00Z',
  isPublishedAtEstimated: false,
  cveId: 'CVE-2026-1001',
  cvssScore: 9.8,
  cvssVersion: '3.1',
  isKev: true,
  isUrgent: true,
  // 나중에 생길 칸 (#67): 몰라도 읽음
  oneLine: '지금 패치하세요',
};

const ALERT_SUMMARY = {
  id: 42,
  kind: 'ALERT',
  alertCategory: 'KEV',
  alertCategories: ['KEV', 'WEATHER', 'CRITICAL_CVE', 'KEV'],
  title: '긴급 보안 소식 3건이 들어왔어요',
  body: '예시 제품 원격 코드 실행 취약점 외 2건',
  leadArticleId: 501,
  leadArticleTitle: '예시 제품 원격 코드 실행 취약점',
  articleCount: 3,
  isPinned: false,
  createdAt: '2026-11-27T00:51:00+09:00',
  updatedAt: '2026-11-27T00:51:00+09:00',
  pushedAt: null,
};

test('목록 한 줄: 모르는 종류·중복 알림 종류를 빼고 시각을 읽는다', () => {
  const summary = toAppNotificationSummary(ALERT_SUMMARY);
  expect(summary).toMatchObject({ id: 42, kind: 'ALERT', alertCategory: 'KEV', alertCategories: ['KEV', 'CRITICAL_CVE'], articleCount: 3 });
  expect(summary?.createdAt.toISOString()).toBe('2026-11-26T15:51:00.000Z');
  expect(summary?.pushedAt).toBeUndefined();
});

test('목록 한 줄: 빠진 칸은 빈 값, 번호·종류·제목·작성 시각이 없으면 버림', () => {
  expect(toAppNotificationSummary({ id: 3, kind: 'NOTICE', title: '점검 안내', createdAt: '2026-11-27T09:00:00+09:00' })).toMatchObject({
    alertCategories: [],
    alertCategory: undefined,
    body: '',
    articleCount: 0,
    isPinned: false,
  });
  expect(toAppNotificationSummary({ ...ALERT_SUMMARY, kind: 'WEATHER' })).toBeUndefined();
  expect(toAppNotificationSummary({ ...ALERT_SUMMARY, id: '42' })).toBeUndefined();
  expect(toAppNotificationSummary({ ...ALERT_SUMMARY, createdAt: 'yesterday' })).toBeUndefined();
  expect(toAppNotificationSummary(null)).toBeUndefined();
});

test('목록 페이지: 형식이 틀린 줄만 버리고 커서를 읽는다', () => {
  const page = toAppNotificationPage({ items: [ALERT_SUMMARY, { id: 1 }, 'broken'], nextCursor: 'abc' });
  expect(page.items.map((item) => item.id)).toEqual([42]);
  expect(page.nextCursor).toBe('abc');
  expect(toAppNotificationPage({})).toEqual({ items: [], nextCursor: undefined });
});

test('상세: 종류별 기사 묶음 (모르는 종류·빈 묶음·깨진 기사는 버림), 새 칸이 있어도 기사를 읽음', () => {
  const detail = toAppNotificationDetail({
    ...ALERT_SUMMARY,
    linkUrl: null,
    articleSections: [
      { alertCategory: 'KEV', articles: [ARTICLE, { id: 'x' }] },
      { alertCategory: 'WEATHER', articles: [ARTICLE] },
      { alertCategory: 'CRITICAL_CVE', articles: [] },
    ],
  });
  expect(detail?.articleSections).toHaveLength(1);
  expect(detail?.articleSections[0].alertCategory).toBe('KEV');
  expect(detail?.articleSections[0].articles[0]).toMatchObject({ id: '501', isKev: true, translatedTitle: '예시 제품 원격 코드 실행 취약점' });
});

test('상세: 공지 링크는 http·https 만, 기사 묶음이 없어도 열림', () => {
  const notice = { id: 9, kind: 'NOTICE', title: '서비스 점검 안내', body: '11월 30일\n새벽 2시부터 점검해요.', createdAt: '2026-11-27T09:00:00+09:00' };
  expect(toAppNotificationDetail({ ...notice, linkUrl: 'https://secbrief.suhsaechan.kr/notice' })?.linkUrl).toBe(
    'https://secbrief.suhsaechan.kr/notice',
  );
  expect(toAppNotificationDetail({ ...notice, linkUrl: 'javascript:alert(1)' })?.linkUrl).toBeUndefined();
  expect(toAppNotificationDetail(notice)?.articleSections).toEqual([]);
  expect(toAppNotificationDetail(notice)?.body).toBe('11월 30일\n새벽 2시부터 점검해요.');
  expect(toAppNotificationDetail({ kind: 'NOTICE' })).toBeUndefined();
});

test('최근 번호: 없거나 숫자가 아니면 없음', () => {
  expect(toLatestAppNotificationIds({ latestAlertId: 42, latestNoticeId: null })).toEqual({ latestAlertId: 42, latestNoticeId: undefined });
  expect(toLatestAppNotificationIds({})).toEqual({ latestAlertId: undefined, latestNoticeId: undefined });
});
