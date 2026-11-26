import type { ArticleBrief } from '@/data/models/article-detail';

/** AI 가 쓴 구역에 한 번 붙이는 표시 (브리핑 카드, 이전 방식 요약 카드 공통) */
export const AI_SUMMARY_NOTICE_LABEL = 'AI 가 요약했어요';

// 브리핑 구역 제목 5개 (화면 문구 단일 원본)
export const BRIEFING_SECTION_TITLES = {
  oneLine: '한 줄 결론',
  whatHappened: '무슨 일이에요',
  whyItMatters: '왜 중요해요',
  affectedTargets: '영향을 받는 대상',
  actions: '이렇게 대응해요',
} as const;

// 원문에 대응이 없을 때의 안내 (대응을 지어내지 않고 원문 보기로 안내)
const EMPTY_ACTION_MESSAGE = '원문에 따로 안내된 대응이 없어요. 자세한 내용은 아래 원문 보기에서 확인해 주세요.';

/** 영향을 받는 대상 한 줄 표시 내용 (값은 서버가 준 그대로) */
export interface AffectedTargetView {
  target: string;
  /** 버전 범위·조건 (원문에 있을 때만) */
  detail?: string;
  accessibilityLabel: string;
}

/**
 * AI 브리핑 표시 내용: 구역 5개 중 한 줄 결론·대응은 항상, 나머지 3개(무슨 일, 왜 중요, 영향 대상)는 값이 있을 때만 보임
 */
export interface ArticleBriefingView {
  aiNoticeLabel: string;
  oneLine: string;
  /** 없으면 구역을 숨김 */
  whatHappened?: string;
  /** 없으면 구역을 숨김 */
  whyItMatters?: string;
  /** 비면 구역을 숨김 */
  affectedTargets: AffectedTargetView[];
  actions: string[];
  /** 대응이 한 건도 없을 때만 있음 (대응 목록 대신 보임) */
  emptyActionMessage?: string;
}

/** 브리핑 > 표시 내용: 한 줄 결론 > 무슨 일 > 왜 중요 > 영향 대상 > 대응(없으면 원문 안내) */
export function toArticleBriefingView(brief: ArticleBrief): ArticleBriefingView {
  return {
    aiNoticeLabel: AI_SUMMARY_NOTICE_LABEL,
    oneLine: brief.oneLine,
    whatHappened: brief.whatHappened,
    whyItMatters: brief.whyItMatters,
    affectedTargets: brief.affectedTargets.map(({ target, detail }) => ({
      target,
      detail,
      accessibilityLabel: detail === undefined ? target : `${target}, ${detail}`,
    })),
    actions: brief.actions,
    emptyActionMessage: brief.actions.length === 0 ? EMPTY_ACTION_MESSAGE : undefined,
  };
}
