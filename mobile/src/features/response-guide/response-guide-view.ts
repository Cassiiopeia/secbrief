import { toCompactDateLabel } from '@/core/format/briefing-date-formatter';
import type { ResponseGuideItem, ResponseGuideSection, ResponseGuideSectionKind } from '@/data/models/response-guide';
import type { SbBadgeContent, SbIconName } from '@/design';

import { type ArticleRowView, EXPLOITED_BADGE_LABEL, toArticleRowView } from '../articles/article-row-view';
import { ACTION_DUE_DATE_PREFIX, RESPONSE_GUIDE_MATCH_LABELS, RESPONSE_GUIDE_SECTION_TEXTS } from './response-guide-texts';

/** 대응 모아보기 1행 표시 내용 (누르면 앱 안 기사 상세: articleRowView 를 미리 보기로 넘김) */
export interface ResponseGuideRowView {
  key: string;
  articleRowView: ArticleRowView;
  /** 행 위 작은 줄: 출처 이름 · 묶인 이유 (예: KISA 보호나라 · 같은 제조사) */
  kickerLabel: string;
  title: string;
  /** 확인된 취약점 정보: CVSS·조치 기한 (원문 값 그대로) */
  factLine?: string;
  /** 한 줄 결론 (AI, 없으면 없음) */
  oneLine?: string;
  /** 악용 중 배지 (KEV 만) */
  badges: SbBadgeContent[];
  dateLabel: string;
  accessibilityLabel: string;
}

/** 대응 모아보기 구역 1개 표시 내용 */
export interface ResponseGuideSectionView {
  key: string;
  section: ResponseGuideSectionKind;
  title: string;
  caption: string;
  iconName: SbIconName;
  isEmphasized: boolean;
  rows: ResponseGuideRowView[];
  /** 보여 준 것보다 더 찾았을 때만 (예: 외 3건이 더 있어요) */
  moreCountLabel?: string;
}

/** 구역 목록 > 표시 내용 (항목이 없는 구역은 뺌, 순서는 받은 순서 = 공식 대응 > 확인된 취약점 정보 > 관련 기사) */
export function toResponseGuideSectionViews(sections: ResponseGuideSection[], now: Date): ResponseGuideSectionView[] {
  return sections
    .filter((section) => section.items.length > 0)
    .map((section) => {
      const texts = RESPONSE_GUIDE_SECTION_TEXTS[section.section];
      const hiddenCount = section.totalCount - section.items.length;
      return {
        key: `guide-${section.section}`,
        section: section.section,
        title: texts.title,
        caption: texts.caption,
        iconName: texts.iconName,
        isEmphasized: texts.isEmphasized,
        rows: section.items.map((item) => toResponseGuideRowView(item, now)),
        moreCountLabel: hiddenCount > 0 ? `외 ${hiddenCount}건이 더 있어요` : undefined,
      };
    });
}

/** 항목 > 행: 출처 · 이유 > 제목 > (악용 중) > (CVSS·조치 기한) > (한 줄 결론) > 날짜. 읽기 도우미는 한 덩어리로 */
export function toResponseGuideRowView(item: ResponseGuideItem, now: Date): ResponseGuideRowView {
  const articleRowView = toArticleRowView(item.article, now);
  const kickerLabel = `${item.sourceName} · ${toMatchLabel(item)}`;
  const factLine = toFactLine(item);
  const dateLabel = toCompactDateLabel(item.article.publishedAt, now);
  const badges: SbBadgeContent[] = item.article.isKev ? [{ label: EXPLOITED_BADGE_LABEL, tone: 'exploited' }] : [];
  const oneLineLabel = item.article.oneLine === undefined ? undefined : `AI 한 줄 결론, ${item.article.oneLine}`;
  return {
    key: `guide-item-${item.article.id}`,
    articleRowView,
    kickerLabel,
    title: articleRowView.title,
    factLine,
    oneLine: item.article.oneLine,
    badges,
    dateLabel,
    accessibilityLabel: [kickerLabel, articleRowView.title, ...badges.map((badge) => badge.label), factLine, oneLineLabel, dateLabel, '자세히 보기']
      .filter((part): part is string => part !== undefined)
      .join(', '),
  };
}

/** 묶인 이유 표시: 같은 CVE 는 번호까지 (예: 같은 CVE · CVE-2026-1111) */
export function toMatchLabel(item: ResponseGuideItem): string {
  const reasonLabel = RESPONSE_GUIDE_MATCH_LABELS[item.matchReason];
  return item.matchReason === 'SAME_CVE' && item.matchedCveId !== undefined ? `${reasonLabel} · ${item.matchedCveId}` : reasonLabel;
}

// 확인된 취약점 정보 줄: CVSS 점수(원문 값) · 조치 기한(원문 값), 둘 다 없으면 없음
function toFactLine(item: ResponseGuideItem): string | undefined {
  const parts = [
    item.article.cvssScore === undefined ? undefined : `CVSS ${item.article.cvssScore.toFixed(1)}`,
    item.actionDueDate === undefined ? undefined : `${ACTION_DUE_DATE_PREFIX} ${item.actionDueDate}`,
  ].filter((part): part is string => part !== undefined);
  return parts.length === 0 ? undefined : parts.join(' · ');
}
