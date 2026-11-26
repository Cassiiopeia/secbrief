/**
 * 대응 모아보기 표시 내용: 구역 문구·강조·외 N건, 행(출처·이유·CVSS·조치 기한·한 줄 결론·읽기 도우미),
 * 기사 상세의 대신 보여 주기(모아보기 > 함께 볼 소식 > 다른 소식 버튼), 알림 상세 기사별 대응 안내 (예시 값, 실제 기사 아님)
 */
import type { AppNotificationArticleGuide } from '@/data/models/app-notification';
import type { ArticleDetail } from '@/data/models/article-detail';
import type { ResponseGuideItem, ResponseGuideSection } from '@/data/models/response-guide';
import { toArticleDetailView } from '@/features/article-detail/article-detail-view';
import { toAlertArticleEntryView } from '@/features/notifications/notification-detail-view';
import { toResponseGuideRowView, toResponseGuideSectionViews } from '@/features/response-guide/response-guide-view';

import { NOW, kisaItem, nvdItem } from './briefing-item-fixtures';

const officialItem: ResponseGuideItem = { article: kisaItem, sourceName: 'KISA 보호나라', matchReason: 'SAME_VENDOR' };
const factItem: ResponseGuideItem = {
  article: { ...nvdItem, id: '12', isKev: true },
  sourceName: 'CISA KEV',
  matchReason: 'SAME_CVE',
  matchedCveId: 'CVE-2026-1111',
  actionDueDate: '2026-12-11',
};
const newsItem: ResponseGuideItem = {
  article: { ...kisaItem, id: '30', kind: 'news', publisherName: 'The Hacker News', title: 'Example VPN flaw exploited', oneLine: '공격이 시작됐어요.' },
  sourceName: 'The Hacker News',
  matchReason: 'SAME_PRODUCT',
};
const allSections: ResponseGuideSection[] = [
  { section: 'OFFICIAL', totalCount: 1, items: [officialItem] },
  { section: 'FACT', totalCount: 1, items: [factItem] },
  { section: 'NEWS', totalCount: 4, items: [newsItem] },
];
const baseDetail: ArticleDetail = {
  article: { ...nvdItem, id: '99' },
  keyPoints: [],
  isAiSummarized: false,
  facts: [],
  relatedItems: [kisaItem],
  responseGuideSections: [],
};

describe('대응 모아보기 구역', () => {
  test('구역 문구: 공식 대응만 강조, 확인된 정보 카드와 겹치지 않는 이름, 더 찾은 건수는 외 N건', () => {
    const views = toResponseGuideSectionViews(allSections, NOW);

    expect(views.map((view) => [view.title, view.isEmphasized])).toEqual([
      ['공식 대응', true],
      ['확인된 취약점 정보', false],
      ['관련 기사', false],
    ]);
    expect(views[0].moreCountLabel).toBeUndefined();
    expect(views[2].moreCountLabel).toBe('외 3건이 더 있어요');
  });

  test('행: 출처 · 이유 (같은 CVE 는 번호까지), KEV 는 악용 중, CVSS·조치 기한은 원문 값 그대로', () => {
    const factRow = toResponseGuideRowView(factItem, NOW);

    expect(factRow.kickerLabel).toBe('CISA KEV · 같은 CVE · CVE-2026-1111');
    expect(factRow.badges).toEqual([{ label: '악용 중', tone: 'exploited' }]);
    expect(factRow.factLine).toBe('CVSS 9.8 · 조치 기한 2026-12-11');
    expect(factRow.articleRowView.id).toBe('12');
    expect(factRow.accessibilityLabel).toContain('CISA KEV · 같은 CVE · CVE-2026-1111');
    expect(factRow.accessibilityLabel.endsWith('자세히 보기')).toBe(true);
  });

  test('행: 공식 공지는 사실 줄·배지 없음, 기사는 한 줄 결론을 AI 표시로 읽어 줌', () => {
    const officialRow = toResponseGuideRowView(officialItem, NOW);
    const newsRow = toResponseGuideRowView(newsItem, NOW);

    expect(officialRow).toMatchObject({ kickerLabel: 'KISA 보호나라 · 같은 제조사', factLine: undefined, badges: [], oneLine: undefined });
    expect(newsRow.kickerLabel).toBe('The Hacker News · 같은 제품');
    expect(newsRow.oneLine).toBe('공격이 시작됐어요.');
    expect(newsRow.accessibilityLabel).toContain('AI 한 줄 결론, 공격이 시작됐어요.');
  });
});

describe('기사 상세: 대응 모아보기 > 함께 볼 소식 > 다른 소식 버튼', () => {
  test('모아보기가 있으면 그것만 (함께 볼 소식·다른 소식 버튼 없음)', () => {
    const view = toArticleDetailView({ ...baseDetail, responseGuideSections: allSections }, NOW);

    expect(view.responseGuideSections).toHaveLength(3);
    expect(view.relatedArticles).toEqual([]);
    expect(view.moreArticlesLink).toBeUndefined();
  });

  test('관련 기사 구역만 있어도 모아보기로 보여 줌', () => {
    const view = toArticleDetailView({ ...baseDetail, responseGuideSections: [allSections[2]] }, NOW);

    expect(view.responseGuideSections.map((section) => section.title)).toEqual(['관련 기사']);
    expect(view.relatedArticles).toEqual([]);
  });

  test('모아보기가 비면 이전처럼 함께 볼 소식, 그것도 없으면 다른 소식 버튼', () => {
    const fallbackView = toArticleDetailView(baseDetail, NOW);
    expect(fallbackView.responseGuideSections).toEqual([]);
    expect(fallbackView.relatedArticles.map((rowView) => rowView.id)).toEqual(['1']);
    expect(fallbackView.moreArticlesLink).toBeUndefined();

    const emptyView = toArticleDetailView({ ...baseDetail, relatedItems: [] }, NOW);
    expect(emptyView.moreArticlesLink).toBeDefined();
  });
});

describe('알림 상세: 알린 기사마다 대응 안내', () => {
  const guide: AppNotificationArticleGuide = {
    articleId: nvdItem.id,
    actions: ['9.4.3 으로 올려요'],
    officialCount: 1,
    factCount: 0,
    newsCount: 5,
    leadOfficialItem: officialItem,
  };

  test('대응 + 첫 공식 대응 + 세 구역 합 건수 버튼 (읽기 도우미는 구역별 건수)', () => {
    const entry = toAlertArticleEntryView(nvdItem, guide, NOW);

    expect(entry.actions).toEqual(['9.4.3 으로 올려요']);
    expect(entry.leadOfficialRow?.kickerLabel).toBe('KISA 보호나라 · 같은 제조사');
    expect(entry.responseGuideButtonLabel).toBe('대응 모아보기 6건 보기');
    expect(entry.responseGuideButtonAccessibilityLabel).toBe(
      '대응 모아보기 6건 보기, 공식 대응 1건, 확인된 취약점 정보 0건, 관련 기사 5건',
    );
    expect(entry.emptyGuideMessage).toBeUndefined();
  });

  test('대응만 있으면 버튼 없이 대응만, 아무것도 없으면 원문 안내 (이전 서버도 같음)', () => {
    const actionsOnly = toAlertArticleEntryView(nvdItem, { ...guide, officialCount: 0, newsCount: 0, leadOfficialItem: undefined }, NOW);
    expect(actionsOnly.responseGuideButtonLabel).toBeUndefined();
    expect(actionsOnly.emptyGuideMessage).toBeUndefined();

    const withoutGuide = toAlertArticleEntryView(nvdItem, undefined, NOW);
    expect(withoutGuide).toMatchObject({ actions: [], leadOfficialRow: undefined, responseGuideButtonLabel: undefined });
    expect(withoutGuide.emptyGuideMessage).toBe('아직 모인 대응 안내가 없어요. 기사를 열어 원문을 확인해 주세요.');
  });
});
