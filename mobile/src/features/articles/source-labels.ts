import {
  CISA_KEV_SOURCE_CODE,
  KISA_SOURCE_CODES,
  LINUX_SOURCE_CODES,
  OPEN_SOURCE_SOURCE_CODES,
  RUNTIME_SOURCE_CODES,
} from '@/data/models/article-source-code';

// 출처 코드 > 표시 이름 (화면이 따로 묶어 보여 주는 출처만)
const SOURCE_LABEL_BY_CODE: Record<string, string> = {
  [KISA_SOURCE_CODES.notice]: 'KISA 보안공지',
  [KISA_SOURCE_CODES.vulnerability]: 'KISA 취약점 정보',
  [KISA_SOURCE_CODES.report]: 'KISA 보고서·가이드',
  [KISA_SOURCE_CODES.alertLevel]: 'KISA 경보단계',
  [CISA_KEV_SOURCE_CODE]: 'CISA KEV 악용 중인 취약점',
  [OPEN_SOURCE_SOURCE_CODES.ghsa]: 'GitHub 보안 권고',
  [OPEN_SOURCE_SOURCE_CODES.ghsaHigh]: 'GitHub 고위험 권고',
  [LINUX_SOURCE_CODES.ubuntu]: 'Ubuntu 보안 공지',
  [LINUX_SOURCE_CODES.debian]: 'Debian 보안 공지',
  [LINUX_SOURCE_CODES.redHat]: 'Red Hat 보안 공지',
  [RUNTIME_SOURCE_CODES.nodeJs]: 'Node.js 보안 릴리스',
};

/** 출처 코드 > 표시 이름 (모르는 코드는 없음) */
export function toSourceLabel(sourceCode: string): string | undefined {
  return SOURCE_LABEL_BY_CODE[sourceCode];
}
