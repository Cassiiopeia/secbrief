import type { Href } from 'expo-router';

import type { ArticleFilter, BriefingTopic } from '@/data/models/briefing-item';

import { toTopicLabel } from '../articles/article-labels';
import { toSourceLabel } from '../articles/source-labels';

/**
 * 모아 보기 화면이 보여 줄 묶음: 제목 + 소식 필터
 * 묶음 키 4종: topic-<주제>, source-<출처 코드>, keyword-<키워드>, kisa-all (KISA 전체)
 */
export interface BrowseTarget {
  title: string;
  filter: ArticleFilter;
}

const TOPIC_KEY_PREFIX = 'topic-';
const SOURCE_KEY_PREFIX = 'source-';
const KEYWORD_KEY_PREFIX = 'keyword-';
export const KISA_ALL_BROWSE_KEY = 'kisa-all';
const KISA_ALL_TITLE = 'KISA 소식';
const HASH_SIGN = '#';

// 모아 볼 수 있는 주제 8종 (일반 제외), 바로가기 표시 순서
export const BROWSABLE_TOPICS: BriefingTopic[] = [
  'vulnerability',
  'ransomware',
  'privacy',
  'remoteAccess',
  'database',
  'supplyChain',
  'policy',
  'event',
];

export const toTopicBrowseKey = (topic: BriefingTopic): string => `${TOPIC_KEY_PREFIX}${topic}`;
export const toSourceBrowseKey = (sourceCode: string): string => `${SOURCE_KEY_PREFIX}${sourceCode}`;
export const toKeywordBrowseKey = (keyword: string): string => `${KEYWORD_KEY_PREFIX}${keyword}`;

/** 묶음 키 > 화면 경로 (app/browse/[browseKey].tsx). 한글 키워드가 들어가므로 인코딩 */
export function toBrowseHref(browseKey: string): Href {
  return `/browse/${encodeURIComponent(browseKey)}`;
}

/** 묶음 키 > 제목과 필터. 모르는 키(없는 주제, 모르는 출처, 빈 키워드)는 없음 */
export function toBrowseTarget(browseKey: string): BrowseTarget | undefined {
  if (browseKey === KISA_ALL_BROWSE_KEY) {
    return { title: KISA_ALL_TITLE, filter: { kind: 'advisory', region: 'domestic' } };
  }
  if (browseKey.startsWith(TOPIC_KEY_PREFIX)) {
    const topic = BROWSABLE_TOPICS.find((candidate) => candidate === browseKey.slice(TOPIC_KEY_PREFIX.length));
    return topic === undefined ? undefined : { title: toTopicLabel(topic), filter: { topic } };
  }
  if (browseKey.startsWith(SOURCE_KEY_PREFIX)) {
    const sourceCode = browseKey.slice(SOURCE_KEY_PREFIX.length);
    const sourceLabel = toSourceLabel(sourceCode);
    return sourceLabel === undefined ? undefined : { title: sourceLabel, filter: { sourceCode } };
  }
  if (browseKey.startsWith(KEYWORD_KEY_PREFIX)) {
    const keyword = browseKey.slice(KEYWORD_KEY_PREFIX.length).trim();
    return keyword === '' ? undefined : { title: `${HASH_SIGN}${keyword}`, filter: { keyword } };
  }
  return undefined;
}
