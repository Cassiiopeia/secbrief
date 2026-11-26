/** 분야·생태계 code 하나의 오늘 건수 + 대표 이름 (원문 표기, 앞 3개) */
export interface TagCount {
  code: string;
  count: number;
  sampleNames: string[];
}

/**
 * 오늘(기기 날짜 0시 이후) 수집한 공개 소식 건수 (#93): 홈 오늘 새 소식 띠, 취약점 탭 칩 +N
 * 분야·생태계는 뉴스가 아닌 항목만, 0건은 서버가 빼고 줌
 */
export interface NewArticleCounts {
  since: Date;
  lastCollectedAt?: Date;
  totalCount: number;
  exploitedCount: number;
  kisaCount: number;
  newsCount: number;
  fieldCounts: TagCount[];
  ecosystemCounts: TagCount[];
  /** 출처 코드 > 건수 */
  countBySourceCode: Record<string, number>;
}
