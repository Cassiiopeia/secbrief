/**
 * 공유 링크 주소: 서버 기사 미리보기 /a/{articleId} (앱이 있으면 유니버설 링크·앱 링크로 앱이 바로 열림)
 * 서버 SharePaths.ARTICLE_PREVIEW, app.json 앱 링크 도메인, +native-intent 경로 변환과 함께 바꿈
 */
import { toApiAbsoluteUrl } from './api-config';

// 서버 SharePaths.ARTICLE_PREVIEW_PREFIX 와 같아야 함
export const SHARED_ARTICLE_PATH_PREFIX = '/a/';

/** 기사 id > 공유 주소 (예: https://api.secbrief.suhsaechan.kr/a/123) */
export function toSharedArticleUrl(articleId: string): string {
  return toApiAbsoluteUrl(`${SHARED_ARTICLE_PATH_PREFIX}${encodeURIComponent(articleId)}`);
}
