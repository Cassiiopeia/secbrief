import type { BriefingItem } from './briefing-item';

/** 원문에서 확인된 값 한 줄 (라벨·값 모두 서버가 준 그대로. 원문에 없으면 서버가 '원문에 없음'을 줌) */
export interface ArticleFact {
  label: string;
  value: string;
}

/** 기사 상세 (앱 안 브리프): 기사 + AI 요약 + 원문에서 확인된 값 + 함께 볼 소식 */
export interface ArticleDetail {
  article: BriefingItem;
  /** AI 가 쓴 한국어 요약 3~5문장 (아직 없으면 없음) */
  summary?: string;
  /** 핵심 2~4개 (아직 없으면 빈 배열) */
  keyPoints: string[];
  /** 원문에 적힌 대응 (원문에 없으면 없음) */
  actionGuide?: string;
  isAiSummarized: boolean;
  facts: ArticleFact[];
  relatedItems: BriefingItem[];
}
