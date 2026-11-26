import type { ArticleImportance, BriefingItem } from '@/data/models/briefing-item';
import type { SbArticleSecondaryLine, SbArticleThumbnail, SbBadgeContent, SbCvssGaugeValue, SbHashtag } from '@/design';

import { toCardHashtags } from '../keywords/keyword-view-state';
import { toArticleBadges, toCvssGaugeValue } from './article-badges';
import { toArticleCover, toArticleRowThumbnail } from './article-cover';
import { toCompactMetaLabel, toCvssLabel, toDisplayTitle, toKindLabel, toMetaLabel, toOneLineAccessibilityLabel } from './article-labels';

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
  /** 게시자 · 짧은 날짜 (· AI 번역) (목록에 보이는 줄) */
  compactMetaLabel: string;
  /** 배지 전체 (목록은 toFeedBadges 로 골라 보여 줌) */
  badges: SbBadgeContent[];
  cvss?: SbCvssGaugeValue;
  /** 카드에 보일 해시태그 (최대 3개) */
  hashtags: SbHashtag[];
  /** 행 오른쪽 썸네일 (뉴스이거나 대표 이미지가 있을 때만) */
  thumbnail?: SbArticleThumbnail;
  /** 큰 카드 위쪽 그림 (항상 있음: 이미지 없으면 커버 그림) */
  cover: SbArticleThumbnail;
  /** 표현 크기를 고르는 기준 */
  importance: ArticleImportance;
  /** 게시자 이름 (원문 보기 버튼) */
  publisherName: string;
  /** 종류 이름 (상세 화면 머리: 뉴스·공지·취약점) */
  kindLabel: string;
  publishedAt: Date;
  accessibilityLabel: string;
  originalUrl: string;
}

/** 소식 > 표시 내용: 제목(번역 우선) > 원문 제목 > 제목 아래 줄 > 메타 > 배지 > CVSS > 해시태그 > 그림 */
export function toArticleRowView(item: BriefingItem, now: Date): ArticleRowView {
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
    badges: toArticleBadges(item),
    cvss: toCvssGaugeValue(item),
    hashtags: toCardHashtags(item.keywords),
    thumbnail: toArticleRowThumbnail(item),
    cover: toArticleCover(item),
    importance: item.importance,
    publisherName: item.publisherName,
    kindLabel: toKindLabel(item.kind),
    publishedAt: item.publishedAt,
    accessibilityLabel: [toDisplayTitle(item), toOneLineAccessibilityLabel(item), metaLabel, cvssLabel, '자세히 보기']
      .filter((part) => part !== undefined)
      .join(', '),
    originalUrl: item.originalUrl,
  };
}

/** 제목 아래 줄 고르기: 한 줄 결론(AI) 우선 > 번역 항목의 원문 제목 > 없음 */
export function toArticleSecondaryLine(oneLine: string | undefined, originalTitle: string | undefined): SbArticleSecondaryLine | undefined {
  if (oneLine !== undefined) return { text: oneLine, tone: 'summary' };
  if (originalTitle !== undefined) return { text: originalTitle, tone: 'reference' };
  return undefined;
}
