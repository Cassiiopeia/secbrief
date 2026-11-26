/**
 * 경보단계 화면 데이터 (GET /api/v1/alert-level): 지금 국내 사이버위기 경보, 변경 이력, 이번 주 위협 건수, 해외 지표
 * 단계는 서버가 KISA 공지 제목에서 읽은 값만 (앱에서 만들지 않음, 모르면 없음)
 */

/** 사이버위기 경보 5단계 (낮은 단계부터): 정상, 관심, 주의, 경계, 심각 */
export const CYBER_CRISIS_LEVELS = ['normal', 'attention', 'caution', 'alert', 'severe'] as const;
export type CyberCrisisLevel = (typeof CYBER_CRISIS_LEVELS)[number];

/** 변경 방향 3개: 상향, 하향·환원, 같은 단계 재발령 */
export type AlertLevelChangeDirection = 'raised' | 'lowered' | 'kept';

/** 해외 지표 2개: 미국 CIS(MS-ISAC) 사이버 경보, SANS 인터넷 스톰 센터 InfoCon */
export const INTERNATIONAL_INDICATOR_CODES = ['cisMsIsac', 'sansIscInfocon'] as const;
export type InternationalIndicatorCode = (typeof INTERNATIONAL_INDICATOR_CODES)[number];

export interface AlertLevelStatus {
  /** 제목에서 못 읽었으면 없음 (화면은 제목 그대로) */
  level?: CyberCrisisLevel;
  /** 이 단계가 된 시각 */
  since: Date;
  articleId: string;
  title: string;
  originalUrl: string;
}

export interface AlertLevelChange {
  articleId: string;
  fromLevel?: CyberCrisisLevel;
  toLevel?: CyberCrisisLevel;
  direction?: AlertLevelChangeDirection;
  publishedAt: Date;
  title: string;
  originalUrl: string;
}

/** 이번 주(서울 월요일부터) 건수 3개 */
export interface WeeklyThreatCounts {
  /** CISA KEV 새 등재 (악용 중) */
  exploitedVulnerabilityCount: number;
  urgentArticleCount: number;
  kisaNoticeCount: number;
}

export interface InternationalIndicator {
  code: InternationalIndicatorCode;
  /** 지표가 준 단계 값 그대로 (예: elevated, green). 못 읽었으면 없음 */
  level?: string;
  /** 1부터 세는 단계 위치 (못 읽었으면 0) */
  levelStep: number;
  levelCount: number;
  checkedAt?: Date;
  sourceUrl: string;
}

export interface AlertLevelDashboard {
  /** 경보단계 글이 하나도 없으면 없음 */
  current?: AlertLevelStatus;
  changes: AlertLevelChange[];
  /** 서버가 주지 않으면 없음 (숫자 구역 생략) */
  weeklyCounts?: WeeklyThreatCounts;
  internationalIndicators: InternationalIndicator[];
  /** KISA 보호나라 경보단계 게시판 (5단계 안내 표) */
  sourceUrl?: string;
}
