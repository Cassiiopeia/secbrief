/**
 * 화면 상태 테스트용 소식 (공개 출처 형식의 예시 값, 실제 기사 아님)
 */
import type { BriefingItem } from '@/data/models/briefing-item';

// 기기 시간대 기준 기준 시각 (2026.11.23 12:00)
export const NOW = new Date(2026, 10, 23, 12, 0);
const PUBLISHED_AT = new Date(2026, 10, 23, 9, 12);

export const kisaItem: BriefingItem = {
  id: '1',
  kind: 'advisory',
  region: 'domestic',
  topic: 'vulnerability',
  importance: 'routine',
  keywords: [],
  title: '예시 제품 보안 업데이트 권고',
  originalUrl: 'https://www.boho.or.kr/kr/bbs/view.do?nttId=1',
  publisherName: 'KISA 보호나라',
  publishedAt: PUBLISHED_AT,
  isPublishedAtEstimated: false,
  isKev: false,
  isUrgent: false,
};

export const nvdItem: BriefingItem = {
  id: '2',
  kind: 'vulnerability',
  region: 'overseas',
  topic: 'remoteAccess',
  importance: 'routine',
  keywords: [],
  title: 'CVE-2026-1111: Example VPN remote code execution',
  originalUrl: 'https://nvd.nist.gov/vuln/detail/CVE-2026-1111',
  publisherName: 'NVD',
  publishedAt: PUBLISHED_AT,
  isPublishedAtEstimated: false,
  cveId: 'CVE-2026-1111',
  cvssScore: 9.8,
  cvssVersion: '3.1',
  isKev: false,
  isUrgent: true,
};

export const kevOnlyItem: BriefingItem = {
  ...nvdItem,
  id: '3',
  title: 'CVE-2026-2222: Example Gateway Improper Authentication',
  originalUrl: 'https://www.cisa.gov/known-exploited-vulnerabilities-catalog?search_api_fulltext=CVE-2026-2222',
  publisherName: 'CISA KEV',
  cveId: 'CVE-2026-2222',
  cvssScore: undefined,
  cvssVersion: undefined,
  isKev: true,
};

export const overseasNewsItem: BriefingItem = {
  id: '4',
  kind: 'news',
  region: 'overseas',
  topic: 'general',
  importance: 'routine',
  keywords: [],
  title: 'Example vendor patches critical flaw',
  originalUrl: 'https://www.bleepingcomputer.com/news/security/example/',
  publisherName: 'BleepingComputer',
  publishedAt: PUBLISHED_AT,
  isPublishedAtEstimated: false,
  isKev: false,
  isUrgent: false,
};
