import type { ArticleImportance, BriefingItem } from '@/data/models/briefing-item';
import type { SbArticleThumbnail, SbBadgeContent, SbCvssGaugeValue, SbHashtag } from '@/design';

import { toCardHashtags } from '../keywords/keyword-view-state';
import { toArticleBadges, toCvssGaugeValue } from './article-badges';
import { toArticleCover, toArticleRowThumbnail } from './article-cover';
import { toCompactMetaLabel, toCvssLabel, toDisplayTitle, toKindLabel, toMetaLabel } from './article-labels';

export * from './article-badges';
export * from './article-labels';

/** 소식 한 건의 표시 내용 (큰 카드·중간 카드·조밀한 행이 함께 씀) + 원문 주소 */
export interface ArticleRowView {
  id: string;
  title: string;
  originalTitle?: string;
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

/** 소식 > 표시 내용: 제목(번역 우선) > 원문 제목 > 메타 > 배지 > CVSS > 해시태그 > 그림 */
export function toArticleRowView(item: BriefingItem, now: Date): ArticleRowView {
  const metaLabel = toMetaLabel(item, now);
  const cvssLabel = toCvssLabel(item);
  return {
    id: item.id,
    title: toDisplayTitle(item),
    originalTitle: item.translatedTitle !== undefined ? item.title : undefined,
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
    accessibilityLabel: [toDisplayTitle(item), metaLabel, cvssLabel, '자세히 보기'].filter((part) => part !== undefined).join(', '),
    originalUrl: item.originalUrl,
  };
}
