import { toArticleDateLabel } from '@/core/format/briefing-date-formatter';
import type { ArticleKind, ArticleRegion, BriefingItem, BriefingTopic } from '@/data/models/briefing-item';

/** 번역 항목 표시 문구 (목록 배지, 오늘 탭 아래 줄) */
export const AI_TRANSLATION_NOTICE_LABEL = 'AI 번역';

// CISA KEV 출처 게시자 이름 (서버 KevCatalogParser.PUBLISHER_NAME 과 같음)
const KEV_PUBLISHER_NAME = 'CISA KEV';
// 추정 게시일 앞에 붙는 말 (출처가 게시일을 안 줘서 수집 시각을 씀)
const ESTIMATED_DATE_PREFIX = '수집 ';

// 주제 8종 > 표시 이름 (화면 문구 단일 원본)
const TOPIC_LABEL_BY_TOPIC: Record<BriefingTopic, string> = {
  vulnerability: '취약점',
  ransomware: '랜섬웨어',
  privacy: '개인정보',
  remoteAccess: '원격 접속',
  database: '데이터베이스',
  supplyChain: '공급망',
  policy: '정책·규제',
  general: '일반',
};
const REGION_LABEL_BY_REGION: Record<ArticleRegion, string> = { domestic: '국내', overseas: '해외' };
const KIND_LABEL_BY_KIND: Record<ArticleKind, string> = { news: '뉴스', advisory: '공지', vulnerability: '취약점' };

/** 소식 목록 행 표시 내용 (SbArticleRow props 와 같은 이름 + 원문 주소) */
export interface ArticleRowView {
  id: string;
  title: string;
  originalTitle?: string;
  translationNoticeLabel?: string;
  metaLabel: string;
  badgeLabels: string[];
  accessibilityLabel: string;
  originalUrl: string;
}

export function toTopicLabel(topic: BriefingTopic): string {
  return TOPIC_LABEL_BY_TOPIC[topic];
}

export function toRegionLabel(region: ArticleRegion): string {
  return REGION_LABEL_BY_REGION[region];
}

export function toKindLabel(kind: ArticleKind): string {
  return KIND_LABEL_BY_KIND[kind];
}

/** 게시자 표기: KEV 등재 항목인데 게시자가 KEV 가 아니면 '게시자 · CISA KEV 등재' */
export function toPublisherLabel(item: BriefingItem): string {
  if (item.isKev && item.publisherName !== KEV_PUBLISHER_NAME) return `${item.publisherName} · CISA KEV 등재`;
  return item.publisherName;
}

/** 크게 보일 제목: 한국어 제목 우선 */
export function toDisplayTitle(item: BriefingItem): string {
  return item.translatedTitle ?? item.title;
}

/** 게시자 · (수집) 날짜 */
export function toMetaLabel(item: BriefingItem, now: Date): string {
  const estimatedPrefix = item.isPublishedAtEstimated ? ESTIMATED_DATE_PREFIX : '';
  return `${toPublisherLabel(item)} · ${estimatedPrefix}${toArticleDateLabel(item.publishedAt, now)}`;
}

/** CVSS 표기 (예: CVSS 9.8 (v3.1)), 점수 없으면 없음 */
export function toCvssLabel(item: BriefingItem): string | undefined {
  if (item.cvssScore === undefined) return undefined;
  const versionSuffix = item.cvssVersion ? ` (v${item.cvssVersion})` : '';
  return `CVSS ${item.cvssScore.toFixed(1)}${versionSuffix}`;
}

/** 소식 > 목록 행: 제목(번역 우선) > 원문 제목 > 메타 > 배지(지역·종류·주제·CVSS·긴급) */
export function toArticleRowView(item: BriefingItem, now: Date): ArticleRowView {
  const isTranslated = item.translatedTitle !== undefined;
  const metaLabel = toMetaLabel(item, now);
  const badgeLabels = [toRegionLabel(item.region), toKindLabel(item.kind), toTopicLabel(item.topic)];
  const cvssLabel = toCvssLabel(item);
  if (cvssLabel !== undefined) badgeLabels.push(cvssLabel);
  if (item.isUrgent) badgeLabels.push('긴급');
  return {
    id: item.id,
    title: toDisplayTitle(item),
    originalTitle: isTranslated ? item.title : undefined,
    translationNoticeLabel: isTranslated ? AI_TRANSLATION_NOTICE_LABEL : undefined,
    metaLabel,
    badgeLabels,
    accessibilityLabel: `${toDisplayTitle(item)}, ${metaLabel}, 원문 열기`,
    originalUrl: item.originalUrl,
  };
}
