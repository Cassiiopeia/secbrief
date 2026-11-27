import type { BriefingItem } from './briefing-item';
import type { ResponseGuideSection } from './response-guide';

/** 원문에서 확인된 값 한 줄 (라벨·값 모두 서버가 준 그대로. 원문에 없으면 서버가 '원문에 없음'을 줌) */
export interface ArticleFact {
  label: string;
  value: string;
}

/** 영향을 받는 대상 한 줄: 대상 이름 + (버전 범위·조건, 원문에 있는 값만) */
export interface ArticleBriefAffectedTarget {
  target: string;
  detail?: string;
}

/** AI 브리핑 (짧은 한 가지 용도 필드 묶음): 한 줄 결론 > 무슨 일 > 왜 중요 > 영향 대상 > 대응 */
export interface ArticleBrief {
  /** 한 줄 결론 (약 60자) */
  oneLine: string;
  /** 무슨 일인지 1~2문장 */
  whatHappened?: string;
  whyItMatters?: string;
  /** 0~4개 */
  affectedTargets: ArticleBriefAffectedTarget[];
  /** 원문에 적힌 대응 0~4개 (원문에 없으면 빈 배열) */
  actions: string[];
}

/** 기사 상세 (앱 안 브리프): 기사 + AI 브리핑(없으면 이전 방식 요약) + 원문에서 확인된 값 + 함께 볼 소식 + 대응 모아보기 */
export interface ArticleDetail {
  article: BriefingItem;
  /** AI 브리핑. 이전 방식 요약만 있는 기사·아직 정리 전인 기사는 없음 */
  brief?: ArticleBrief;
  /** AI 가 쓴 한국어 요약 3~5문장 (아직 없으면 없음) */
  summary?: string;
  /** 핵심 2~4개 (아직 없으면 빈 배열) */
  keyPoints: string[];
  /** 원문에 적힌 대응 (원문에 없으면 없음) */
  actionGuide?: string;
  isAiSummarized: boolean;
  /** AI 요약을 기다리는 중 (서버 isSummaryPending, 이 값을 주지 않는 이전 서버는 false: 요약 카드를 숨김) */
  isSummaryPending: boolean;
  facts: ArticleFact[];
  relatedItems: BriefingItem[];
  /** 같은 취약점을 다룬 공개 자료 (구역 순서대로, 없으면 빈 배열: 그때는 함께 볼 소식을 보여 줌) */
  responseGuideSections: ResponseGuideSection[];
}
