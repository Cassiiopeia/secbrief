import type { AppNotificationDetail } from '@/data/models/app-notification';

import { type ArticleRowView, toArticleRowView } from '../articles/article-row-view';
import type { TagCatalogIndex } from '../catalog/tag-catalog-view';

/** 아침 요약 분야 묶음 1개 표시 내용 (분야 이름은 카탈로그, 모르는 분야는 code 대신 분류 전) */
export interface DigestFieldSectionView {
  fieldCode: string;
  title: string;
  /** 대표 이름 (앞 2개 + 외) */
  detail?: string;
  countLabel: string;
  rowViews: ArticleRowView[];
}

/** 아침 요약 화면 표시 내용: 머리 타일 2개 > 꼭 볼 것 카드 > 분야별 묶음 */
export interface DigestDetailView {
  articleCountLabel: string;
  fieldCountLabel: string;
  notableRowViews: ArticleRowView[];
  fieldSections: DigestFieldSectionView[];
}

// 카탈로그에 없는(못 받았거나 지워진) 분야 묶음 이름
const UNKNOWN_FIELD_TITLE = '분류 전';
const SAMPLE_NAME_COUNT = 2;

/** 알림 상세(DIGEST) > 아침 요약 표시 내용 */
export function toDigestDetailView(detail: AppNotificationDetail, catalogIndex: TagCatalogIndex, now: Date): DigestDetailView {
  const digest = detail.digest ?? { articleCount: detail.articleCount, notableArticles: [], fieldSections: [] };
  const fieldSections = digest.fieldSections.map((section) => {
    const rowViews = section.articles.map((article) => toArticleRowView(article, now, catalogIndex));
    const sampleNames = [...new Set(rowViews.map((rowView) => rowView.leadingName).filter((name): name is string => name !== undefined))];
    return {
      fieldCode: section.fieldCode,
      title: catalogIndex.findTag('FIELD', section.fieldCode)?.name ?? UNKNOWN_FIELD_TITLE,
      detail: sampleNames.length === 0 ? undefined : toSampleNamesLabel(sampleNames),
      countLabel: `${section.articles.length}건`,
      rowViews,
    };
  });
  return {
    articleCountLabel: `${digest.articleCount}건`,
    fieldCountLabel: `${fieldSections.length}개`,
    notableRowViews: digest.notableArticles.map((article) => toArticleRowView(article, now, catalogIndex)),
    fieldSections,
  };
}

// 대표 이름 2개 + 외 (예: WP Job Portal, Budibase 외)
function toSampleNamesLabel(sampleNames: string[]): string {
  const shownNames = sampleNames.slice(0, SAMPLE_NAME_COUNT).join(', ');
  return sampleNames.length > SAMPLE_NAME_COUNT ? `${shownNames} 외` : shownNames;
}
