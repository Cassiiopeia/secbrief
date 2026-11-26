import { SHARED_ARTICLE_PATH_PREFIX } from '@/core/config/share-link-config';

import { toArticleDetailRoute } from './app-routes';

// 공유 링크 경로: (스킴://호스트)/a/{숫자 id}(/)(?쿼리·#조각). https 는 앱 링크 도메인만 앱으로 들어옴, secbrief:///a/{id} 도 같음
const SHARED_ARTICLE_LINK_PATTERN = new RegExp(
  `^(?:[a-z][a-z0-9+.-]*://[^/?#]*)?${SHARED_ARTICLE_PATH_PREFIX}(\\d+)/?(?:[?#].*)?$`,
  'i',
);

/**
 * 앱으로 들어온 링크 > 앱 화면 경로: 공유 링크(/a/{id}) > 기사 상세, 그 밖(secbrief://articles/{id} 등)은 그대로
 * 앱이 꺼져 있을 때·켜져 있을 때 모두 +native-intent 가 부름
 */
export function toAppPathFromIncomingLink(incomingPath: string): string {
  const sharedArticleMatch = SHARED_ARTICLE_LINK_PATTERN.exec(incomingPath.trim());
  return sharedArticleMatch === null ? incomingPath : toArticleDetailRoute(sharedArticleMatch[1]);
}
