import type { BriefingItem } from './briefing-item';

/** 대응 모아보기 구역 3개 (서버 ResponseGuideSection, 화면 순서): 공식 대응 > 확인된 취약점 정보 > 관련 기사 */
export const RESPONSE_GUIDE_SECTIONS = ['OFFICIAL', 'FACT', 'NEWS'] as const;
export type ResponseGuideSectionKind = (typeof RESPONSE_GUIDE_SECTIONS)[number];

/** 묶인 이유 3개 (서버 ResponseGuideMatchReason, 확실한 순): 같은 CVE > 같은 제품 > 같은 제조사 공지 */
export const RESPONSE_GUIDE_MATCH_REASONS = ['SAME_CVE', 'SAME_PRODUCT', 'SAME_VENDOR'] as const;
export type ResponseGuideMatchReason = (typeof RESPONSE_GUIDE_MATCH_REASONS)[number];

/** 대응 모아보기 1건: 앱 안 기사 + 출처 이름 + 묶인 이유 (+ 겹친 CVE, KEV 조치 기한: 원문 값 그대로) */
export interface ResponseGuideItem {
  article: BriefingItem;
  /** 예: KISA 보호나라, Red Hat 보안 권고 */
  sourceName: string;
  matchReason: ResponseGuideMatchReason;
  matchedCveId?: string;
  actionDueDate?: string;
}

/** 대응 모아보기 구역 1개: 찾은 전체 건수 + 보여 줄 항목 (서버가 5건까지 줌) */
export interface ResponseGuideSection {
  section: ResponseGuideSectionKind;
  totalCount: number;
  items: ResponseGuideItem[];
}
