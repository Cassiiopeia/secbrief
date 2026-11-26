/**
 * 경보단계 화면: 서버 응답 읽기(모르는 값·깨진 줄 버림), 머리 문장, 변경 이력 문구, 이번 주 숫자 목록 연결, 해외 지표(읽음·못 읽음), 홈 경보 띠
 */
import { toAlertLevelDashboard } from '@/data/mappers/alert-level-mapper';
import type { AlertLevelDashboard } from '@/data/models/alert-level-dashboard';
import { toAlertLevelDashboardView } from '@/features/alert-level/alert-level-dashboard-view';
import { toAlertLevelView } from '@/features/today/alert-level-view';

const NOW = new Date('2026-11-27T00:00:00Z');
const KISA_ARTICLE_URL = 'https://www.boho.or.kr/kr/bbs/view.do?nttId=72233';

// 서버 응답 모양 (AlertLevelControllerTest 와 같은 값)
const API_RESPONSE = {
  current: { level: 'CAUTION', since: '2026-10-21T15:00:00Z', articleId: 794, title: "사이버위기 경보 단계 '관심' → '주의' 상향 조정", originalUrl: KISA_ARTICLE_URL },
  changes: [
    { articleId: 794, fromLevel: 'ATTENTION', toLevel: 'CAUTION', direction: 'RAISED', publishedAt: '2026-10-21T15:00:00Z', title: '상향', originalUrl: KISA_ARTICLE_URL },
    { articleId: 800, fromLevel: null, toLevel: 'CAUTION', direction: null, publishedAt: '2022-03-20T15:00:00Z', title: "'주의' 경보 발령", originalUrl: KISA_ARTICLE_URL },
    { articleId: 1, fromLevel: null, toLevel: null, direction: null, publishedAt: '2004-07-12T15:00:00Z', title: "'경고' 단계 발령", originalUrl: KISA_ARTICLE_URL },
    { articleId: 2, title: '깨진 줄', originalUrl: 'javascript:alert(1)' },
  ],
  weeklyCounts: { weekStart: '2026-11-22T15:00:00Z', exploitedVulnerabilityCount: 4, urgentArticleCount: 12, kisaNoticeCount: 9 },
  internationalIndicators: [
    { code: 'CIS_MS_ISAC', level: 'elevated', levelStep: 3, levelCount: 5, checkedAt: '2026-11-26T23:00:00Z', sourceUrl: 'https://www.cisecurity.org/cybersecurity-threats/alert-level' },
    { code: 'SANS_ISC_INFOCON', level: null, levelStep: 0, levelCount: 4, checkedAt: null, sourceUrl: 'https://isc.sans.edu/infocon.html' },
    { code: 'FUTURE_INDICATOR', level: 'x', levelStep: 1, levelCount: 3, sourceUrl: 'https://example.test' },
  ],
  sourceUrl: 'https://www.boho.or.kr/kr/bbs/list.do?menuNo=205024&bbsId=B0000342',
};

const toDashboard = (): AlertLevelDashboard => toAlertLevelDashboard(API_RESPONSE);

describe('경보단계 응답 읽기', () => {
  test('단계·방향·지표 코드를 앱 값으로, 깨진 줄과 모르는 지표는 버림', () => {
    const dashboard = toDashboard();
    expect(dashboard.current).toMatchObject({ level: 'caution', articleId: '794' });
    expect(dashboard.changes.map((change) => change.articleId)).toEqual(['794', '800', '1']);
    expect(dashboard.changes[0]).toMatchObject({ fromLevel: 'attention', toLevel: 'caution', direction: 'raised' });
    expect(dashboard.internationalIndicators.map((indicator) => indicator.code)).toEqual(['cisMsIsac', 'sansIscInfocon']);
    expect(dashboard.internationalIndicators[1]).toMatchObject({ level: undefined, levelStep: 0, levelCount: 4 });
  });

  test('이전 서버(칸 없음)도 빈 화면으로 열림', () => {
    expect(toAlertLevelDashboard({})).toEqual({
      current: undefined,
      changes: [],
      weeklyCounts: undefined,
      internationalIndicators: [],
      sourceUrl: undefined,
    });
  });
});

describe('경보단계 화면 표시', () => {
  test('머리: 단계 이름, 시작일 문장, 5단계 중 위치, KISA 게시판 링크', () => {
    const hero = toAlertLevelDashboardView(toDashboard(), NOW).hero;
    expect(hero).toMatchObject({ levelName: '주의', tone: 'yellow', currentStep: 3, sourceUrl: API_RESPONSE.sourceUrl });
    // 서울 10-22 00:00 = 기기 시간대가 서울일 때의 날짜 (테스트 시간대는 jest 설정)
    expect(hero?.sinceLabel).toMatch(/^10월 2[12]일부터 주의 단계예요$/);
    expect(hero?.gaugeSegments.map((segment) => segment.label)).toEqual(['정상', '관심', '주의', '경계', '심각']);
  });

  test('단계를 못 읽은 공지는 제목 그대로 + 원문 확인 안내', () => {
    const hero = toAlertLevelDashboardView({ ...toDashboard(), current: { ...toDashboard().current!, level: undefined } }, NOW).hero;
    expect(hero?.levelName).toBeUndefined();
    expect(hero).toMatchObject({ rawTitle: API_RESPONSE.current.title, currentStep: 0 });
    expect(hero?.meaning).toContain('원문에서 확인');
  });

  test('변경 이력: 전후 단계 + 방향, 전 단계를 모르면 발령, 단계를 모르면 공지 제목', () => {
    const changes = toAlertLevelDashboardView(toDashboard(), NOW).changes;
    expect(changes.map((change) => change.title)).toEqual(['관심 → 주의 상향', '주의 발령', "'경고' 단계 발령"]);
    expect(changes[0].originalUrl).toBe(KISA_ARTICLE_URL);
  });

  test('이번 주 숫자 3개는 각 목록으로 연결 (KEV 출처, 긴급만, KISA 보안공지)', () => {
    const tiles = toAlertLevelDashboardView(toDashboard(), NOW).weeklyTiles;
    expect(tiles.map((tile) => [tile.valueText, tile.browseKey])).toEqual([
      ['+4', 'source-CISA_KEV'],
      ['12', 'urgent'],
      ['9', 'source-KISA'],
    ]);
  });

  test('해외 지표: 지표 자체 단계 수만큼 막대, 읽은 값은 뜻·확인 시각, 못 읽은 지표는 안내 문구', () => {
    const [cis, sans] = toAlertLevelDashboardView(toDashboard(), NOW).indicators;
    expect(cis).toMatchObject({ name: '미국 CIS(MS-ISAC) 사이버 경보', levelLabel: 'Elevated', currentStep: 3 });
    expect(cis.gaugeSegments.map((segment) => segment.tone)).toEqual(['green', 'blue', 'yellow', 'orange', 'red']);
    expect(cis.captionLabel).toBe('5단계 중 3단계 · 미국 주·지방 정부 기관 대상 위협 기준 · 1시간 전 확인');
    expect(sans.levelLabel).toBeUndefined();
    expect(sans).toMatchObject({ name: 'SANS 인터넷 스톰 센터 InfoCon', currentStep: 0 });
    expect(sans.gaugeSegments).toHaveLength(4);
    expect(sans.meaning).toBe('지금은 확인하지 못했어요. 원문에서 확인해 주세요.');
  });
});

describe('홈 사이버위기 경보 띠', () => {
  test('지금 단계 · 시작일 (공지 제목이 아님)', () => {
    expect(toAlertLevelView(toDashboard())).toMatchObject({ capsLabel: '사이버위기 경보', tone: 'yellow' });
    expect(toAlertLevelView(toDashboard())?.title).toMatch(/^주의 · 10\.2[12]부터$/);
  });

  test('경보단계 글이 없으면 띠 없음', () => {
    expect(toAlertLevelView({ ...toDashboard(), current: undefined })).toBeUndefined();
  });
});
