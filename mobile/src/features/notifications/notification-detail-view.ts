import { toAbsoluteDate, toDateTimeLabel } from '@/core/format/briefing-date-formatter';
import type { AlertCategory } from '@/data/models/alert-category';
import type { AppNotificationArticleGuide, AppNotificationDetail } from '@/data/models/app-notification';
import type { BriefingItem } from '@/data/models/briefing-item';
import type { SbBadgeContent } from '@/design';

import { type ArticleRowView, toArticleRowView } from '../articles/article-row-view';
import { RESPONSE_GUIDE_SECTION_TEXTS } from '../response-guide/response-guide-texts';
import { type ResponseGuideRowView, toResponseGuideRowView } from '../response-guide/response-guide-view';
import { ALERT_CATEGORY_TEXTS } from './alert-category-texts';

/**
 * 긴급 알림 상세 구역 (화면은 kind 로 그릴 것을 고름)
 * categoryArticles: 알림 종류 하나 + 왜 알렸는지 + 그 기사들(기사마다 대응 안내). 새 구역은 kind 를 더해 붙임
 */
export type AlertDetailBlock = {
  kind: 'categoryArticles';
  key: string;
  alertCategory: AlertCategory;
  /** 예: 악용 중인 취약점 2건 */
  title: string;
  /** 왜 알렸는지 (예: 실제 공격에 쓰인 것이 확인된 취약점이에요 (CISA KEV 등재)) */
  reason: string;
  entries: AlertArticleEntryView[];
};

/**
 * 알린 기사 1건: 기사 행 > 대응 안내 (이렇게 대응해요 > 공식 대응 첫 건 > 대응 모아보기 N건 버튼)
 * 대응도 모아보기도 없으면 원문 안내 한 줄
 */
export interface AlertArticleEntryView {
  key: string;
  rowView: ArticleRowView;
  /** 원문에 적힌 대응 (AI 브리핑, 없으면 빈 배열) */
  actions: string[];
  leadOfficialRow?: ResponseGuideRowView;
  /** 대응 모아보기 버튼 문구 (모은 자료가 없으면 없음, 예: 대응 모아보기 6건 보기) */
  responseGuideButtonLabel?: string;
  /** 대응 모아보기 버튼 읽기 도우미 문구 (구역별 건수) */
  responseGuideButtonAccessibilityLabel?: string;
  /** 대응도 모아보기도 없을 때 안내 */
  emptyGuideMessage?: string;
}

// 대응 안내가 하나도 없는 기사의 안내 (원문 보기는 기사 상세 아래 줄)
const EMPTY_GUIDE_MESSAGE = '아직 모인 대응 안내가 없어요. 기사를 열어 원문을 확인해 주세요.';

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

/** 상세 > 긴급 알림 표시 내용: 종류별 구역(왜 알렸는지) > 기사마다 대응 안내 (기사가 없는 종류는 구역을 만들지 않음) */
export function toAlertDetailView(detail: AppNotificationDetail, now: Date): AlertDetailView {
  const guideByArticleId = new Map(detail.articleGuides.map((guide) => [guide.articleId, guide]));
  const blocks: AlertDetailBlock[] = detail.articleSections.map((section) => ({
    kind: 'categoryArticles',
    key: `category-${section.alertCategory}`,
    alertCategory: section.alertCategory,
    title: `${ALERT_CATEGORY_TEXTS[section.alertCategory].title} ${section.articles.length}건`,
    reason: ALERT_CATEGORY_TEXTS[section.alertCategory].reason,
    entries: section.articles.map((article) => toAlertArticleEntryView(article, guideByArticleId.get(article.id), now)),
  }));
  const articleCount = blocks.reduce((count, block) => count + block.entries.length, 0);
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

/**
 * 알린 기사 1건 > 표시 내용 (대응 안내 요약이 없는 기사: 이전 서버이거나 21번째 이후 기사는 원문 안내만)
 * 버튼 건수는 세 구역 합 (공식 대응 · 확인된 취약점 정보 · 관련 기사)
 */
export function toAlertArticleEntryView(article: BriefingItem, guide: AppNotificationArticleGuide | undefined, now: Date): AlertArticleEntryView {
  const actions = guide?.actions ?? [];
  const responseGuideCount = guide === undefined ? 0 : guide.officialCount + guide.factCount + guide.newsCount;
  const leadOfficialRow = guide?.leadOfficialItem && toResponseGuideRowView(guide.leadOfficialItem, now);
  const hasGuide = responseGuideCount > 0;
  return {
    key: `alert-article-${article.id}`,
    rowView: toArticleRowView(article, now),
    actions,
    leadOfficialRow,
    responseGuideButtonLabel: hasGuide ? `대응 모아보기 ${responseGuideCount}건 보기` : undefined,
    responseGuideButtonAccessibilityLabel:
      hasGuide && guide !== undefined
        ? [
            `대응 모아보기 ${responseGuideCount}건 보기`,
            `${RESPONSE_GUIDE_SECTION_TEXTS.OFFICIAL.title} ${guide.officialCount}건`,
            `${RESPONSE_GUIDE_SECTION_TEXTS.FACT.title} ${guide.factCount}건`,
            `${RESPONSE_GUIDE_SECTION_TEXTS.NEWS.title} ${guide.newsCount}건`,
          ].join(', ')
        : undefined,
    emptyGuideMessage: !hasGuide && actions.length === 0 ? EMPTY_GUIDE_MESSAGE : undefined,
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
