/**
 * 소식 주제 5종 (커버 그림 고르는 기준)
 * vulnerability 취약점·침투, ransomware 랜섬웨어, privacy 개인정보보호,
 * remoteAccess 원격 접속·명령 실행, database 데이터베이스
 */
export type BriefingTopic = 'vulnerability' | 'ransomware' | 'privacy' | 'remoteAccess' | 'database';

/** 브리핑 소식 한 건 (보안 공지 또는 뉴스) */
export interface BriefingItem {
  id: string;
  title: string;
  /** 출처 이름 (KISA, 개인정보위, 해외 등) */
  sourceName: string;
  publishedAt: Date;
  topic: BriefingTopic;
  /** CVSS 점수 (취약점 공지에만 있음) */
  cvssScore?: number;
  /** 대상 제품 이름 (Tomcat 등) */
  productName?: string;
  /** 사용자가 고른 제품에 해당하는 소식인지 */
  isMyStackMatch: boolean;
}
