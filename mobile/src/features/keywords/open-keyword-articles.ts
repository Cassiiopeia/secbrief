import { router } from 'expo-router';

import { toBrowseHref, toKeywordBrowseKey } from '../browse/browse-target';

/** 해시태그 누름 > 그 키워드 소식 모아 보기 화면 */
export function openKeywordArticles(keyword: string): void {
  router.push(toBrowseHref(toKeywordBrowseKey(keyword)));
}
