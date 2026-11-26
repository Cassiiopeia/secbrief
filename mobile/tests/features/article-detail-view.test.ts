/**
 * 기사 상세 표시 내용: 브리핑이 있으면 브리핑 구역, 없으면 이전 방식 요약, 아무것도 없으면 준비 중 (예시 값, 실제 기사 아님)
 */
import type { ArticleBrief, ArticleDetail } from '@/data/models/article-detail';
import { BRIEFING_SECTION_TITLES, toArticleBriefingView } from '@/features/article-detail/article-briefing-view';
import { toArticleDetailView } from '@/features/article-detail/article-detail-view';

import { NOW, kisaItem, nvdItem } from './briefing-item-fixtures';

const fullBrief: ArticleBrief = {
  oneLine: '예시 게이트웨이의 인증 우회 취약점이 공개됐어요',
  whatHappened: '예시 게이트웨이 관리 화면에서 인증 없이 명령을 실행할 수 있는 취약점이 공개됐어요.',
  whyItMatters: '관리 화면이 인터넷에 열려 있으면 바로 공격받을 수 있어요.',
  affectedTargets: [{ target: '예시 게이트웨이 9.x', detail: '9.0.0 ~ 9.4.2' }, { target: '예시 게이트웨이 8.x' }],
  actions: ['9.4.3 으로 올려요', '관리 화면을 내부망에서만 열어요'],
};
const newsBrief: ArticleBrief = { oneLine: '예시 기관이 보안 점검 안내서를 냈어요', whatHappened: '안내서가 공개됐어요.', affectedTargets: [], actions: [] };

const baseDetail: ArticleDetail = { article: nvdItem, keyPoints: [], isAiSummarized: false, facts: [], relatedItems: [] };

describe('브리핑 표시 내용 (보일 구역 고르기)', () => {
  test('값이 다 있으면 구역 5개가 모두 보인다', () => {
    const briefing = toArticleBriefingView(fullBrief);
    expect(briefing).toMatchObject({
      aiNoticeLabel: 'AI 가 요약했어요',
      oneLine: fullBrief.oneLine,
      whatHappened: fullBrief.whatHappened,
      whyItMatters: fullBrief.whyItMatters,
      actions: fullBrief.actions,
      emptyActionMessage: undefined,
    });
    expect(briefing.affectedTargets).toHaveLength(2);
  });
  test('왜 중요·영향 대상이 없으면 그 구역 값이 없고, 대응이 없으면 원문 안내 한 줄', () => {
    const briefing = toArticleBriefingView(newsBrief);
    expect(briefing.whyItMatters).toBeUndefined();
    expect(briefing.affectedTargets).toEqual([]);
    expect(briefing.actions).toEqual([]);
    expect(briefing.emptyActionMessage).toBe('원문에 따로 안내된 대응이 없어요. 자세한 내용은 아래 원문 보기에서 확인해 주세요.');
  });
  test('영향 대상은 받은 값 그대로, 읽기 도우미 문구는 대상과 조건을 이어서', () => {
    expect(toArticleBriefingView(fullBrief).affectedTargets).toEqual([
      { target: '예시 게이트웨이 9.x', detail: '9.0.0 ~ 9.4.2', accessibilityLabel: '예시 게이트웨이 9.x, 9.0.0 ~ 9.4.2' },
      { target: '예시 게이트웨이 8.x', detail: undefined, accessibilityLabel: '예시 게이트웨이 8.x' },
    ]);
  });
  test('구역 제목은 해요체 짧은 글', () => {
    expect(BRIEFING_SECTION_TITLES).toEqual({
      oneLine: '한 줄 결론',
      whatHappened: '무슨 일이에요',
      whyItMatters: '왜 중요해요',
      affectedTargets: '영향을 받는 대상',
      actions: '이렇게 대응해요',
    });
  });
});

describe('상세 표시 내용 (브리핑 / 이전 방식 / 준비 중)', () => {
  test('브리핑이 있으면 briefing 이 있다 (이전 방식 값이 함께 와도)', () => {
    const view = toArticleDetailView({ ...baseDetail, brief: fullBrief, summary: '이전 요약', keyPoints: ['핵심'], isAiSummarized: true }, NOW);
    expect(view.briefing?.oneLine).toBe(fullBrief.oneLine);
  });
  test('브리핑이 없고 이전 방식 요약이 있으면 이전 방식 그대로', () => {
    const view = toArticleDetailView({ ...baseDetail, summary: '이전 요약', keyPoints: [' 핵심 ', ''], actionGuide: '대응', isAiSummarized: true }, NOW);
    expect(view.briefing).toBeUndefined();
    expect(view).toMatchObject({ summary: '이전 요약', keyPoints: ['핵심'], actionGuide: '대응', aiNoticeLabel: 'AI 가 요약했어요', isSummaryPending: false });
  });
  test('AI 내용이 하나도 없으면 준비 중 (제목·확인된 정보·원문 보기만)', () => {
    const view = toArticleDetailView({ ...baseDetail, article: kisaItem, facts: [{ label: '조치 버전', value: '원문에 없음' }] }, NOW);
    expect(view.briefing).toBeUndefined();
    expect(view).toMatchObject({ summary: undefined, keyPoints: [], aiNoticeLabel: undefined, isSummaryPending: true });
    expect(view.facts).toEqual([{ label: '조치 버전', value: '원문에 없음' }]);
  });
});
