import { toArticleDateLabel, toCompactDateLabel } from '@/core/format/briefing-date-formatter';
import type { ArticleKind, ArticleRegion, BriefingItem, BriefingTopic } from '@/data/models/briefing-item';

/** 번역 아이콘의 읽기 도우미 문구 (목록·홈·상세의 번역 표시 공통) */
export const AI_TRANSLATED_TITLE_ACCESSIBILITY_LABEL = 'AI가 번역한 제목이에요';

// 추정 게시일 앞에 붙는 말 (출처가 게시일을 안 줘서 수집 시각을 씀)
const ESTIMATED_DATE_PREFIX = '수집 ';

// 주제 9종 > 표시 이름 (화면 문구 단일 원본)
const TOPIC_LABEL_BY_TOPIC: Record<BriefingTopic, string> = {
  vulnerability: '취약점',
  ransomware: '랜섬웨어',
  privacy: '개인정보',
  remoteAccess: '원격 접속',
  database: '데이터베이스',
  supplyChain: '공급망',
  policy: '정책·규제',
  event: '행사',
  general: '일반',
};
const REGION_LABEL_BY_REGION: Record<ArticleRegion, string> = { domestic: '국내', overseas: '해외' };
const KIND_LABEL_BY_KIND: Record<ArticleKind, string> = { news: '뉴스', advisory: '공지', vulnerability: '취약점' };

export function toTopicLabel(topic: BriefingTopic): string {
  return TOPIC_LABEL_BY_TOPIC[topic];
}

export function toRegionLabel(region: ArticleRegion): string {
  return REGION_LABEL_BY_REGION[region];
}

export function toKindLabel(kind: ArticleKind): string {
  return KIND_LABEL_BY_KIND[kind];
}

/** 크게 보일 제목: 한국어 제목 우선 */
export function toDisplayTitle(item: BriefingItem): string {
  return item.translatedTitle ?? item.title;
}

/** 게시자 · (수집) 절대 날짜 · 상대 표기 (읽기 도우미, 상세 머리) */
export function toMetaLabel(item: BriefingItem, now: Date): string {
  return `${item.publisherName} · ${toEstimatedPrefix(item)}${toArticleDateLabel(item.publishedAt, now)}`;
}

/** 한 줄 결론을 읽기 도우미가 읽을 때 앞에 붙이는 말 (화면에서는 반짝 아이콘이 대신함) */
const AI_SUMMARY_ACCESSIBILITY_PREFIX = 'AI 요약';

/** 한 줄 결론의 읽기 도우미 문구 (예: AI 요약, 결론), 한 줄 결론이 없으면 없음 */
export function toOneLineAccessibilityLabel(item: BriefingItem): string | undefined {
  return item.oneLine === undefined ? undefined : `${AI_SUMMARY_ACCESSIBILITY_PREFIX}, ${item.oneLine}`;
}

/** 번역 항목이면 번역 아이콘 읽기 도우미 문구, 아니면 없음 (아이콘도 없음) */
export function toTranslatedMarkLabel(item: BriefingItem): string | undefined {
  return item.translatedTitle !== undefined ? AI_TRANSLATED_TITLE_ACCESSIBILITY_LABEL : undefined;
}

/** 게시자 · (수집) 짧은 날짜 (카드·행·스트림에 보이는 줄) */
export function toCompactMetaLabel(item: BriefingItem, now: Date): string {
  return `${item.publisherName} · ${toEstimatedPrefix(item)}${toCompactDateLabel(item.publishedAt, now)}`;
}

function toEstimatedPrefix(item: BriefingItem): string {
  return item.isPublishedAtEstimated ? ESTIMATED_DATE_PREFIX : '';
}

/** CVSS 표기 (예: CVSS 9.8 (v3.1)), 점수 없으면 없음 */
export function toCvssLabel(item: BriefingItem): string | undefined {
  if (item.cvssScore === undefined) return undefined;
  const versionSuffix = item.cvssVersion ? ` (v${item.cvssVersion})` : '';
  return `CVSS ${item.cvssScore.toFixed(1)}${versionSuffix}`;
}
