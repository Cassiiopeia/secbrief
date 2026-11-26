import { toAppPathFromIncomingLink } from '@/features/navigation/incoming-link-route';

/** 들어온 링크 > 앱 경로 (Expo Router 가 앱 시작·실행 중 링크마다 부름): 공유 링크 /a/{id} > 기사 상세 */
export function redirectSystemPath({ path }: { path: string; initial: boolean }): string {
  return toAppPathFromIncomingLink(path);
}
