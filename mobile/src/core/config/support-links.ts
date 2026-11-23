/**
 * 문의·약관 주소 단일 원본 (스토어 등록정보·site/support.html 과 함께 바꿈)
 */
export const supportLinks = {
  contactEmail: 'chan4760@gmail.com',
  supportPageUrl: 'https://cassiiopeia.github.io/secbrief/support.html',
  privacyPolicyUrl: 'https://cassiiopeia.github.io/secbrief/privacy.html',
  termsUrl: 'https://cassiiopeia.github.io/secbrief/terms.html',
} as const;

/** 문의 메일 주소: 받는 사람 + 제목(앱 버전 포함) */
export function toContactMailUrl(appVersion: string): string {
  return `mailto:${supportLinks.contactEmail}?subject=${encodeURIComponent(`[시큐브리프] 문의 (앱 ${appVersion})`)}`;
}
