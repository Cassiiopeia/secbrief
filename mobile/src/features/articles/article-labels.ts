import { toArticleDateLabel, toCompactDateLabel } from '@/core/format/briefing-date-formatter';
import { CISA_KEV_SOURCE_CODE } from '@/data/models/article-source-code';
import type { ArticleKind, ArticleRegion, BriefingItem, BriefingTopic } from '@/data/models/briefing-item';

/** 번역 항목 표시 문구 (상세 머리 배지, 목록·홈의 메타 줄 꼬리) */
export const AI_TRANSLATION_NOTICE_LABEL = 'AI 번역';

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

/** KEV 가 직접 올린 항목인지 (출처 코드 기준) */
function isPublishedByKev(item: BriefingItem): boolean {
  return item.sourceCode === CISA_KEV_SOURCE_CODE;
}

/** 게시자 표기: KEV 등재 항목인데 게시자가 KEV 가 아니면 '게시자 · CISA KEV 등재' */
export function toPublisherLabel(item: BriefingItem): string {
  if (item.isKev && !isPublishedByKev(item)) return `${item.publisherName} · CISA KEV 등재`;
  return item.publisherName;
}

/** 크게 보일 제목: 한국어 제목 우선 */
export function toDisplayTitle(item: BriefingItem): string {
  return item.translatedTitle ?? item.title;
}

/** 게시자 · (수집) 절대 날짜 · 상대 표기 (읽기 도우미, 상세 머리) */
export function toMetaLabel(item: BriefingItem, now: Date): string {
  return `${toPublisherLabel(item)} · ${toEstimatedPrefix(item)}${toArticleDateLabel(item.publishedAt, now)}`;
}

// 번역 항목 표시 꼬리 (AI 번역 배지가 없는 자리의 메타 줄 끝에 붙임)
const TRANSLATION_SUFFIX = ` · ${AI_TRANSLATION_NOTICE_LABEL}`;

/** 한 줄 결론을 읽기 도우미가 읽을 때 앞에 붙이는 말 (화면에서는 반짝 아이콘이 대신함) */
const AI_SUMMARY_ACCESSIBILITY_PREFIX = 'AI 요약';

/** 한 줄 결론의 읽기 도우미 문구 (예: AI 요약, 결론), 한 줄 결론이 없으면 없음 */
export function toOneLineAccessibilityLabel(item: BriefingItem): string | undefined {
  return item.oneLine === undefined ? undefined : `${AI_SUMMARY_ACCESSIBILITY_PREFIX}, ${item.oneLine}`;
}

/** 번역 항목이면 ' · AI 번역', 아니면 빈 글 */
export function toTranslationSuffix(item: BriefingItem): string {
  return item.translatedTitle !== undefined ? TRANSLATION_SUFFIX : '';
}

/** 게시자 · (수집) 짧은 날짜 (· AI 번역) (카드·행·스트림에 보이는 줄) */
export function toCompactMetaLabel(item: BriefingItem, now: Date): string {
  return `${item.publisherName} · ${toEstimatedPrefix(item)}${toCompactDateLabel(item.publishedAt, now)}${toTranslationSuffix(item)}`;
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
