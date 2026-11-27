/**
 * 브리핑 응답 변환: 없음·null·다른 형태·빈 글·상한 초과를 모두 견딤 (예시 값, 실제 기사 아님)
 */
import { toArticleBrief } from '@/data/mappers/article-brief-mapper';
import { toNonBlankText } from '@/data/mappers/unknown-value-guards';
import { type ArticleApiResponse, toBriefingItem } from '@/data/mappers/api-response-mapper';
import { toArticleDetail } from '@/data/mappers/article-detail-mapper';

const fullBriefResponse = {
  oneLine: '예시 게이트웨이의 인증 우회 취약점이 공개됐어요',
  whatHappened: '예시 게이트웨이 관리 화면에서 인증 없이 명령을 실행할 수 있는 취약점이 공개됐어요.',
  whyItMatters: '관리 화면이 인터넷에 열려 있으면 바로 공격받을 수 있어요.',
  affected: [
    { target: '예시 게이트웨이 9.x', detail: '9.0.0 ~ 9.4.2' },
    { target: '예시 게이트웨이 8.x', detail: null },
  ],
  actions: ['9.4.3 으로 올려요', '관리 화면을 내부망에서만 열어요'],
};

const sampleArticleResponse: ArticleApiResponse = {
  id: 7,
  kind: 'VULNERABILITY',
  region: 'OVERSEAS',
  topic: 'VULNERABILITY',
  title: 'CVE-2026-1111: Example remote code execution',
  translatedTitle: null,
  originalUrl: 'https://nvd.nist.gov/vuln/detail/CVE-2026-1111',
  publisherName: 'NVD',
  publishedAt: '2026-11-23T00:00:00Z',
  isPublishedAtEstimated: false,
  cveId: 'CVE-2026-1111',
  cvssScore: 9.8,
  cvssVersion: '3.1',
  isKev: true,
  isUrgent: true,
};

test('값이 다 있으면 받은 그대로 (detail null 은 없음)', () => {
  expect(toArticleBrief(fullBriefResponse)).toEqual({
    oneLine: fullBriefResponse.oneLine,
    whatHappened: fullBriefResponse.whatHappened,
    whyItMatters: fullBriefResponse.whyItMatters,
    affectedTargets: [
      { target: '예시 게이트웨이 9.x', detail: '9.0.0 ~ 9.4.2' },
      { target: '예시 게이트웨이 8.x', detail: undefined },
    ],
    actions: fullBriefResponse.actions,
  });
});
test('브리핑이 없거나 객체가 아니면 없음', () => {
  for (const response of [undefined, null, '', '브리핑', 3, true, [], [fullBriefResponse]]) {
    expect(toArticleBrief(response)).toBeUndefined();
  }
});
test('한 줄 결론이 없거나 빈 글·다른 형태면 기사의 oneLine 으로, 그것도 없으면 브리핑 없음', () => {
  for (const oneLine of [undefined, null, '', '   ', 7, ['결론']]) {
    expect(toArticleBrief({ ...fullBriefResponse, oneLine })).toBeUndefined();
    expect(toArticleBrief({ ...fullBriefResponse, oneLine }, '기사 한 줄 결론')?.oneLine).toBe('기사 한 줄 결론');
  }
  // 브리핑 값이 있으면 그쪽이 우선
  expect(toArticleBrief(fullBriefResponse, '기사 한 줄 결론')?.oneLine).toBe(fullBriefResponse.oneLine);
});
test('한 줄 결론만 있어도 브리핑 (나머지는 없음·빈 배열)', () => {
  expect(toArticleBrief({ oneLine: ' 결론 ' })).toEqual({
    oneLine: '결론',
    whatHappened: undefined,
    whyItMatters: undefined,
    affectedTargets: [],
    actions: [],
  });
});
test('글 필드: null·빈 글·다른 형태는 없음, 앞뒤 빈칸은 뺌', () => {
  const brief = toArticleBrief({ ...fullBriefResponse, whatHappened: '  무슨 일  ', whyItMatters: null });
  expect(brief?.whatHappened).toBe('무슨 일');
  expect(brief?.whyItMatters).toBeUndefined();
  expect(toArticleBrief({ ...fullBriefResponse, whatHappened: 12, whyItMatters: ' ' })).toMatchObject({
    whatHappened: undefined,
    whyItMatters: undefined,
  });
});
test('영향 대상: 목록이 아니면 빈 배열, 대상 이름이 없는 줄·형태가 다른 줄은 뺌', () => {
  for (const affected of [undefined, null, '예시 제품', { target: '예시 제품' }, 3]) {
    expect(toArticleBrief({ ...fullBriefResponse, affected })?.affectedTargets).toEqual([]);
  }
  const affected = [null, '예시 제품', { detail: '1.0' }, { target: '', detail: '1.0' }, { target: ' 예시 서버 ', detail: 3 }, { target: '예시 앱', detail: ' 2.1 미만 ' }];
  expect(toArticleBrief({ ...fullBriefResponse, affected })?.affectedTargets).toEqual([
    { target: '예시 서버', detail: undefined },
    { target: '예시 앱', detail: '2.1 미만' },
  ]);
});
test('영향 대상·대응은 4개까지만 (넘으면 앞에서부터)', () => {
  const affected = [1, 2, 3, 4, 5, 6].map((targetNumber) => ({ target: `예시 제품 ${targetNumber}`, detail: null }));
  const actions = [1, 2, 3, 4, 5, 6].map((actionNumber) => `대응 ${actionNumber}`);
  const brief = toArticleBrief({ ...fullBriefResponse, affected, actions });
  expect(brief?.affectedTargets.map((affectedTarget) => affectedTarget.target)).toEqual(['예시 제품 1', '예시 제품 2', '예시 제품 3', '예시 제품 4']);
  expect(brief?.actions).toEqual(['대응 1', '대응 2', '대응 3', '대응 4']);
});
test('대응: 목록이 아니면 빈 배열, 글이 아닌 줄·빈 줄은 뺌', () => {
  for (const actions of [undefined, null, '패치해요', { first: '패치해요' }]) {
    expect(toArticleBrief({ ...fullBriefResponse, actions })?.actions).toEqual([]);
  }
  expect(toArticleBrief({ ...fullBriefResponse, actions: ['', null, 3, ' 패치해요 ', {}, '  '] })?.actions).toEqual(['패치해요']);
});
test('toNonBlankText: 글만 받고 빈 글은 없음', () => {
  expect(toNonBlankText(' 글 ')).toBe('글');
  expect([toNonBlankText(''), toNonBlankText('  '), toNonBlankText(null), toNonBlankText(undefined), toNonBlankText(0)]).toEqual([
    undefined, undefined, undefined, undefined, undefined,
  ]);
});
test('목록 항목의 oneLine: 값이 있으면 그대로, null·빈 글·필드 없음·다른 형태는 없음', () => {
  expect(toBriefingItem({ ...sampleArticleResponse, oneLine: ' 한 줄 결론 ' }).oneLine).toBe('한 줄 결론');
  expect(toBriefingItem({ ...sampleArticleResponse, oneLine: null }).oneLine).toBeUndefined();
  expect(toBriefingItem({ ...sampleArticleResponse, oneLine: '' }).oneLine).toBeUndefined();
  // 이 필드가 생기기 전 서버 응답 (필드 자체가 없음)
  expect(toBriefingItem(sampleArticleResponse).oneLine).toBeUndefined();
  expect(toBriefingItem({ ...sampleArticleResponse, oneLine: 5 as unknown as string }).oneLine).toBeUndefined();
});
test('기사 상세: brief 가 없거나 null 이면 없음 (이전 방식 요약은 그대로)', () => {
  const legacyResponse = { article: sampleArticleResponse, summary: '요약', keyPoints: ['핵심'], actionGuide: '대응', isAiSummarized: true };
  expect(toArticleDetail(legacyResponse).brief).toBeUndefined();
  expect(toArticleDetail({ ...legacyResponse, brief: null })).toMatchObject({ brief: undefined, summary: '요약', keyPoints: ['핵심'], actionGuide: '대응' });
  // 기사에 oneLine 만 있고 brief 가 null 이면 브리핑으로 보지 않음
  expect(toArticleDetail({ ...legacyResponse, article: { ...sampleArticleResponse, oneLine: '결론' }, brief: null }).brief).toBeUndefined();
});
test('기사 상세: brief 가 있으면 브리핑, 한 줄 결론이 비면 기사의 oneLine 으로 채움', () => {
  expect(toArticleDetail({ article: sampleArticleResponse, brief: fullBriefResponse }).brief?.oneLine).toBe(fullBriefResponse.oneLine);
  const detail = toArticleDetail({ article: { ...sampleArticleResponse, oneLine: '기사 결론' }, brief: { ...fullBriefResponse, oneLine: null } });
  expect(detail.brief?.oneLine).toBe('기사 결론');
});
