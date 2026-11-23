/**
 * 소식 주제 8종 (서버 ArticleTopic 과 같은 값, 커버 그림 고르는 기준)
 * vulnerability 취약점, ransomware 랜섬웨어, privacy 개인정보, remoteAccess 원격 접속,
 * database 데이터베이스, supplyChain 공급망, policy 정책·규제, general 일반(분류 전·분류 실패)
 */
export type BriefingTopic =
  | 'vulnerability'
  | 'ransomware'
  | 'privacy'
  | 'remoteAccess'
  | 'database'
  | 'supplyChain'
  | 'policy'
  | 'general';

/** 소식 종류: news 뉴스, advisory 보안 공지, vulnerability 취약점(KEV·NVD·GHSA) */
export type ArticleKind = 'news' | 'advisory' | 'vulnerability';

/** 지역: domestic 국내, overseas 해외 */
export type ArticleRegion = 'domestic' | 'overseas';

/** 소식 한 건 (서버 ArticleResponse 와 같은 필드 이름) */
export interface BriefingItem {
  id: string;
  kind: ArticleKind;
  region: ArticleRegion;
  topic: BriefingTopic;
  /** 원문 제목 */
  title: string;
  /** AI 한국어 제목 (해외 항목이고 검증 통과한 경우만) */
  translatedTitle?: string;
  originalUrl: string;
  publisherName: string;
  publishedAt: Date;
  /** 출처가 게시일을 안 줘서 수집 시각으로 대신함 */
  isPublishedAtEstimated: boolean;
  cveId?: string;
  cvssScore?: number;
  /** 점수 버전 (예: 3.1, 4.0) */
  cvssVersion?: string;
  /** CISA KEV 등재 */
  isKev: boolean;
  isUrgent: boolean;
}

/** 목록 필터 (값이 없으면 전체) */
export interface ArticleFilter {
  kind?: ArticleKind;
  region?: ArticleRegion;
  topic?: BriefingTopic;
}
