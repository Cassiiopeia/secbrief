/**
 * 소식 주제 9종 (서버 ArticleTopic 과 같은 값, 커버 그림 고르는 기준): 취약점, 랜섬웨어, 개인정보, 원격 접속, 데이터베이스, 공급망, 정책·규제,
 * event 행사(컨퍼런스·세미나·교육·CFP), general 일반(분류 전·분류 실패)
 */
export type BriefingTopic =
  | 'vulnerability'
  | 'ransomware'
  | 'privacy'
  | 'remoteAccess'
  | 'database'
  | 'supplyChain'
  | 'policy'
  | 'event'
  | 'general';

/** 소식 종류: news 뉴스, advisory 보안 공지, vulnerability 취약점(KEV·NVD·GHSA) */
export type ArticleKind = 'news' | 'advisory' | 'vulnerability';

/** 지역: domestic 국내, overseas 해외 */
export type ArticleRegion = 'domestic' | 'overseas';

/**
 * 중요도 3단 (AI 가 제목으로 판단, 서버 ArticleImportance 와 같은 값)
 * headline 큰 소식(널리 쓰는 제품의 악용 중 취약점·대규모 사고·국가 경보), notable 주목할 소식, routine 그 밖(판단 전 포함)
 */
export type ArticleImportance = 'headline' | 'notable' | 'routine';

/** 소식 한 건 (서버 ArticleResponse 와 같은 필드 이름) */
export interface BriefingItem {
  id: string;
  kind: ArticleKind;
  region: ArticleRegion;
  topic: BriefingTopic;
  importance: ArticleImportance;
  /** 제목에 나온 회사·제품 이름 (예: VMware). AI 가 뽑음, 없으면 없음 */
  subjectName?: string;
  /** 출처 코드 (예: KISA_REPORT, CISA_KEV). 이 필드가 생기기 전 서버는 안 줌 */
  sourceCode?: string;
  /** AI 가 제목에서 뽑은 키워드 (0~5개, # 없이). 이 필드가 생기기 전 서버는 빈 배열 */
  keywords: string[];
  /** 원문 제목 */
  title: string;
  /** AI 한국어 제목 (해외 항목이고 검증 통과한 경우만) */
  translatedTitle?: string;
  /** AI 가 쓴 한 줄 결론 (약 60자). 브리핑이 아직 없거나 이 필드가 생기기 전 서버면 없음 */
  oneLine?: string;
  originalUrl: string;
  /** 대표 이미지 서버 경로 (예: /api/v1/articles/123/image). 이미지가 없으면 없음 */
  imagePath?: string;
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
  /** 이 중요도 이상만 (notable 이면 notable + headline) */
  minimumImportance?: Exclude<ArticleImportance, 'routine'>;
  /** 출처 1곳만 */
  sourceCode?: string;
  /** 이 키워드가 붙은 소식만 (# 없이) */
  keyword?: string;
  /** true 면 긴급 소식만 (경보단계 화면의 이번 주 긴급 건수에서 들어옴) */
  isUrgent?: boolean;
}
