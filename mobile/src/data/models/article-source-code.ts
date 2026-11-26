/**
 * 화면이 구분해 쓰는 출처 코드 (서버 Source.code 와 같은 값)
 */
// CISA 악용 중 취약점 목록
export const CISA_KEV_SOURCE_CODE = 'CISA_KEV';

/** KISA 출처 4곳: 보안공지, 취약점 정보, 보고서·가이드, 경보단계 */
export const KISA_SOURCE_CODES = {
  notice: 'KISA',
  vulnerability: 'KISA_VULNERABILITY',
  report: 'KISA_REPORT',
  alertLevel: 'KISA_ALERT_LEVEL',
} as const;

export type KisaSourceCode = (typeof KISA_SOURCE_CODES)[keyof typeof KISA_SOURCE_CODES];

/** 취약점 탭 출처 묶음 3개의 출처 코드: 오픈소스(GitHub 권고), 리눅스 배포판, 런타임 */
export const OPEN_SOURCE_SOURCE_CODES = { ghsa: 'GHSA', ghsaHigh: 'GHSA_HIGH' } as const;
export const LINUX_SOURCE_CODES = { ubuntu: 'UBUNTU_USN', debian: 'DEBIAN_DSA', redHat: 'REDHAT_RHSA' } as const;
export const RUNTIME_SOURCE_CODES = { nodeJs: 'NODEJS_SECURITY' } as const;
