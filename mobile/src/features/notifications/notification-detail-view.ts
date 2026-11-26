import { toAbsoluteDate, toDateTimeLabel } from '@/core/format/briefing-date-formatter';
import type { AlertCategory } from '@/data/models/alert-category';
import type { AppNotificationDetail } from '@/data/models/app-notification';
import type { SbBadgeContent } from '@/design';

import { type ArticleRowView, toArticleRowView } from '../articles/article-row-view';
import { ALERT_CATEGORY_TEXTS } from './alert-category-texts';

/**
 * 긴급 알림 상세 구역 (화면은 kind 로 그릴 것을 고름)
 * categoryArticles: 알림 종류 하나 + 왜 알렸는지 + 그 기사들. 기사별 대응 모아 보기 같은 구역은 kind 를 더해 붙임 (#71)
 */
export type AlertDetailBlock = {
  kind: 'categoryArticles';
  key: string;
  alertCategory: AlertCategory;
  /** 예: 악용 중인 취약점 2건 */
  title: string;
  /** 왜 알렸는지 (예: 실제 공격에 쓰인 것이 확인된 취약점이에요 (CISA KEV 등재)) */
  reason: string;
  rowViews: ArticleRowView[];
};

/** 긴급 알림 상세 표시 내용: 머리(배지·제목·시각·요약 한 줄) + 구역들 */
export interface AlertDetailView {
  badges: SbBadgeContent[];
  title: string;
  /** 예: 2026.11.27 00:51 · 3시간 전 */
  timeLabel: string;
  /** 머리 아래 한 줄 (여러 건을 묶은 알림만) */
  summaryLine?: string;
  blocks: AlertDetailBlock[];
}

/** 공지사항 상세 표시 내용 */
export interface NoticeDetailView {
  title: string;
  /** 예: 2026.11.27 (수정한 공지는 · 2026.11.28 수정) */
  dateLabel: string;
  isPinned: boolean;
  body: string;
  linkUrl?: string;
}

// 수정 표시를 붙이는 최소 차이 (작성 직후 고친 것은 수정으로 보지 않음: 10분)
const EDITED_MINIMUM_MILLISECONDS = 10 * 60 * 1000;

/** 상세 > 긴급 알림 표시 내용 (기사가 없는 종류는 구역을 만들지 않음) */
export function toAlertDetailView(detail: AppNotificationDetail, now: Date): AlertDetailView {
  const blocks: AlertDetailBlock[] = detail.articleSections.map((section) => ({
    kind: 'categoryArticles',
    key: `category-${section.alertCategory}`,
    alertCategory: section.alertCategory,
    title: `${ALERT_CATEGORY_TEXTS[section.alertCategory].title} ${section.articles.length}건`,
    reason: ALERT_CATEGORY_TEXTS[section.alertCategory].reason,
    rowViews: section.articles.map((article) => toArticleRowView(article, now)),
  }));
  const articleCount = blocks.reduce((count, block) => count + block.rowViews.length, 0);
  return {
    badges: detail.alertCategories.map((category) => ({
      label: ALERT_CATEGORY_TEXTS[category].badgeLabel,
      tone: ALERT_CATEGORY_TEXTS[category].badgeTone,
    })),
    title: detail.title,
    timeLabel: toDateTimeLabel(detail.createdAt, now),
    summaryLine: articleCount > 1 ? `긴급 소식 ${articleCount}건을 종류별로 모았어요.` : undefined,
    blocks,
  };
}

/** 상세 > 공지사항 표시 내용 */
export function toNoticeDetailView(detail: AppNotificationDetail): NoticeDetailView {
  const isEdited = detail.updatedAt.getTime() - detail.createdAt.getTime() >= EDITED_MINIMUM_MILLISECONDS;
  return {
    title: detail.title,
    dateLabel: isEdited ? `${toAbsoluteDate(detail.createdAt)} · ${toAbsoluteDate(detail.updatedAt)} 수정` : toAbsoluteDate(detail.createdAt),
    isPinned: detail.isPinned,
    body: detail.body,
    linkUrl: detail.linkUrl,
  };
}
