import type { ArticleBrief, ArticleBriefAffectedTarget } from '../models/article-detail';
import { isNonArrayObject, toNonBlankText } from './unknown-value-guards';

// api-response-mapper 가 이 파일에서 가져다 씀 (원본은 unknown-value-guards)
export { toNonBlankText } from './unknown-value-guards';

/** 서버 브리핑 응답 (서버 ArticleBriefResponse 와 같은 필드 이름) */
export interface ArticleBriefApiResponse {
  oneLine?: string | null;
  whatHappened?: string | null;
  whyItMatters?: string | null;
  affected?: { target?: string | null; detail?: string | null }[] | null;
  actions?: string[] | null;
}

// 서버 계약의 목록 상한 (넘게 오면 앞에서부터 이만큼만 씀)
const MAXIMUM_AFFECTED_TARGET_COUNT = 4;
const MAXIMUM_ACTION_COUNT = 4;

/**
 * 브리핑 응답 > 브리핑 (형태를 믿지 않고 필드마다 확인: 없음·null·다른 형태·빈 글은 없는 값으로)
 * 한 줄 결론은 브리핑 값 > 기사의 oneLine 순. 둘 다 없으면 브리핑 없음 (화면은 이전 방식 요약으로)
 */
export function toArticleBrief(response: unknown, articleOneLine?: string): ArticleBrief | undefined {
  if (!isNonArrayObject(response)) return undefined;
  const oneLine = toNonBlankText(response.oneLine) ?? articleOneLine;
  if (oneLine === undefined) return undefined;
  return {
    oneLine,
    whatHappened: toNonBlankText(response.whatHappened),
    whyItMatters: toNonBlankText(response.whyItMatters),
    affectedTargets: toAffectedTargets(response.affected),
    actions: toActions(response.actions),
  };
}

// 영향 대상 목록: 대상 이름이 있는 줄만, 상한까지 (detail 은 없어도 됨)
function toAffectedTargets(response: unknown): ArticleBriefAffectedTarget[] {
  if (!Array.isArray(response)) return [];
  return response
    .flatMap((affectedResponse: unknown): ArticleBriefAffectedTarget[] => {
      if (!isNonArrayObject(affectedResponse)) return [];
      const target = toNonBlankText(affectedResponse.target);
      return target === undefined ? [] : [{ target, detail: toNonBlankText(affectedResponse.detail) }];
    })
    .slice(0, MAXIMUM_AFFECTED_TARGET_COUNT);
}

// 대응 목록: 글인 줄만, 상한까지
function toActions(response: unknown): string[] {
  if (!Array.isArray(response)) return [];
  return response.flatMap((actionResponse: unknown) => toNonBlankText(actionResponse) ?? []).slice(0, MAXIMUM_ACTION_COUNT);
}
