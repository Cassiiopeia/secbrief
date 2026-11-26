import { toCompactDateLabel } from '@/core/format/briefing-date-formatter';
import type { ArticleImportance, BriefingItem } from '@/data/models/briefing-item';
import type { SbArticleSecondaryLine, SbBadgeContent, SbCvssGaugeValue, SbHashtag } from '@/design';

import { type TagCatalogIndex, toTagBadges } from '../catalog/tag-catalog-view';
import { toCardHashtags } from '../keywords/keyword-view-state';
import { toArticleBadges, toCvssGaugeValue } from './article-badges';
import { toArticleImageUrl } from './article-cover';
import {
  toCompactMetaLabel,
  toCvssLabel,
  toDisplayTitle,
  toKindLabel,
  toMetaLabel,
  toOneLineAccessibilityLabel,
  toTranslatedMarkLabel,
} from './article-labels';

export * from './article-badges';
export * from './article-labels';

/** 소식 한 건의 표시 내용 (큰 카드·중간 카드·조밀한 행이 함께 씀) + 원문 주소 */
export interface ArticleRowView {
  id: string;
  title: string;
  /** 번역 항목의 원문 제목 (상세 머리) */
  originalTitle?: string;
  /** 목록 카드·행의 제목 아래 작은 줄: 한 줄 결론, 없으면 번역 항목의 원문 제목, 둘 다 없으면 없음 */
  secondaryLine?: SbArticleSecondaryLine;
  /** 게시자 · 절대 날짜 · 상대 표기 (읽기 도우미용 전체 표기) */
  metaLabel: string;
  /** 게시자 · 짧은 날짜 (목록에 보이는 줄) */
  compactMetaLabel: string;
  /** 번역 항목의 번역 아이콘 읽기 도우미 문구 (번역 아니면 없음: 아이콘 없음) */
  translatedMarkLabel?: string;
  /** 배지 전체 (목록은 toFeedBadges 로 골라 보여 줌) */
  badges: SbBadgeContent[];
  /** 분류 태그 배지 (#93, 카탈로그를 준 목록만: 취약점 탭). 비면 기존 배지 */
  tagBadges: SbBadgeContent[];
  /** 한 줄 행 앞 굵은 이름 (패키지 > 카탈로그 제품 > 제목 속 이름) */
  leadingName?: string;
  /** 한 줄 행 오른쪽 (생태계·분야 짧은 이름 · 짧은 날짜) */
  oneLineTrailingLabel: string;
  cvss?: SbCvssGaugeValue;
  /** 카드에 보일 해시태그 (최대 3개) */
  hashtags: SbHashtag[];
  /** 대표 이미지 절대 주소 (행 썸네일·큰 카드·상세 머리 공통). 없으면 그림 없이 글로, 제목 전체 */
  imageUrl?: string;
  /** 표현 크기를 고르는 기준 */
  importance: ArticleImportance;
  /** 게시자 이름 (원문 보기 버튼) */
  publisherName: string;
  /** 종류 이름 (상세 화면 머리: 뉴스·공지·취약점) */
  kindLabel: string;
  publishedAt: Date;
  accessibilityLabel: string;
  originalUrl: string;
  /** 출처 코드 (경보단계 글은 기사 상세 대신 경보단계 화면으로 열기, 이전 서버면 없음) */
  sourceCode?: string;
}

/** 소식 > 표시 내용: 제목(번역 우선) > 원문 제목 > 제목 아래 줄 > 메타 > 배지(카탈로그를 주면 분류 태그도) > CVSS > 해시태그 > 대표 이미지 */
export function toArticleRowView(item: BriefingItem, now: Date, catalogIndex?: TagCatalogIndex): ArticleRowView {
  const metaLabel = toMetaLabel(item, now);
  const cvssLabel = toCvssLabel(item);
  const originalTitle = item.translatedTitle !== undefined ? item.title : undefined;
  return {
    id: item.id,
    title: toDisplayTitle(item),
    originalTitle,
    secondaryLine: toArticleSecondaryLine(item.oneLine, originalTitle),
    metaLabel,
    compactMetaLabel: toCompactMetaLabel(item, now),
    translatedMarkLabel: toTranslatedMarkLabel(item),
    badges: toArticleBadges(item),
    tagBadges: catalogIndex === undefined ? [] : toTagBadges(item, catalogIndex, toCvssGaugeValue(item) !== undefined),
    leadingName: item.packageName ?? (catalogIndex?.findTag('PRODUCT', item.productCode)?.shortName ?? item.subjectName),
    oneLineTrailingLabel: [
      catalogIndex?.findTag('ECOSYSTEM', item.ecosystemCode)?.shortName ?? catalogIndex?.findTag('FIELD', item.fieldCode)?.shortName,
      toCompactDateLabel(item.publishedAt, now),
    ]
      .filter((part) => part !== undefined)
      .join(' · '),
    cvss: toCvssGaugeValue(item),
    hashtags: toCardHashtags(item.keywords),
    imageUrl: toArticleImageUrl(item),
    importance: item.importance,
    publisherName: item.publisherName,
    kindLabel: toKindLabel(item.kind),
    publishedAt: item.publishedAt,
    accessibilityLabel: [toDisplayTitle(item), toTranslatedMarkLabel(item), toOneLineAccessibilityLabel(item), metaLabel, cvssLabel, '자세히 보기']
      .filter((part) => part !== undefined)
      .join(', '),
    originalUrl: item.originalUrl,
    sourceCode: item.sourceCode,
  };
}

/** 제목 아래 줄 고르기: 한 줄 결론(AI) 우선 > 번역 항목의 원문 제목 > 없음 */
export function toArticleSecondaryLine(oneLine: string | undefined, originalTitle: string | undefined): SbArticleSecondaryLine | undefined {
  if (oneLine !== undefined) return { text: oneLine, tone: 'summary' };
  if (originalTitle !== undefined) return { text: originalTitle, tone: 'reference' };
  return undefined;
}
