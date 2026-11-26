import { ALERT_CATEGORIES, type AlertCategory } from '../models/alert-category';
import {
  APP_NOTIFICATION_KINDS,
  type AppNotificationArticleGuide,
  type AppNotificationArticleSection,
  type AppNotificationDetail,
  type AppNotificationKind,
  type AppNotificationPage,
  type AppNotificationSummary,
  type LatestAppNotificationIds,
} from '../models/app-notification';
import { isArticleApiResponse, toBriefingItem } from './api-response-mapper';
import { toResponseGuideItem } from './response-guide-mapper';

/** 서버 알림함 목록 한 줄 (모든 칸을 믿지 않고 읽음: 이전·이후 서버가 칸을 빼거나 더할 수 있음) */
export interface AppNotificationSummaryApiResponse {
  id?: unknown;
  kind?: unknown;
  alertCategory?: unknown;
  alertCategories?: unknown;
  title?: unknown;
  body?: unknown;
  leadArticleId?: unknown;
  leadArticleTitle?: unknown;
  articleCount?: unknown;
  isPinned?: unknown;
  createdAt?: unknown;
  updatedAt?: unknown;
  pushedAt?: unknown;
}

export interface AppNotificationDetailApiResponse extends AppNotificationSummaryApiResponse {
  linkUrl?: unknown;
  articleSections?: unknown;
  /** 기사별 대응 안내 요약 (#71). 이 필드가 생기기 전 서버는 아예 안 줌 */
  articleGuides?: unknown;
}

export interface AppNotificationPageApiResponse {
  items?: unknown;
  nextCursor?: unknown;
}

export interface LatestAppNotificationIdsApiResponse {
  latestAlertId?: unknown;
  latestNoticeId?: unknown;
}

// 공지 링크로 여는 주소 모양 (http·https 만)
const WEB_LINK_PATTERN = /^https?:\/\//i;

/** 목록 응답 > 한 페이지 (형식이 틀린 줄은 버림) */
export function toAppNotificationPage(response: AppNotificationPageApiResponse): AppNotificationPage {
  const items = Array.isArray(response.items) ? response.items : [];
  return {
    items: items.map(toAppNotificationSummary).filter((item): item is AppNotificationSummary => item !== undefined),
    nextCursor: toOptionalText(response.nextCursor),
  };
}

/**
 * 목록 한 줄 응답 > 알림함 글 (번호·종류·제목·작성 시각이 없거나 모르는 종류면 버림)
 * 모르는 알림 종류는 빼고, 그 밖의 칸은 없으면 빈 값
 */
export function toAppNotificationSummary(response: unknown): AppNotificationSummary | undefined {
  if (!isObject(response)) return undefined;
  const value = response as AppNotificationSummaryApiResponse;
  const id = toPositiveInteger(value.id);
  const kind = toAppNotificationKind(value.kind);
  const createdAt = toDate(value.createdAt);
  if (id === undefined || kind === undefined || createdAt === undefined || typeof value.title !== 'string') return undefined;
  const alertCategories = toAlertCategoriesInOrder(value.alertCategories);
  const alertCategory = toAlertCategory(value.alertCategory) ?? alertCategories[0];
  return {
    id,
    kind,
    alertCategory,
    alertCategories: alertCategories.length > 0 || alertCategory === undefined ? alertCategories : [alertCategory],
    title: value.title,
    body: typeof value.body === 'string' ? value.body : '',
    leadArticleId: toPositiveInteger(value.leadArticleId),
    leadArticleTitle: toOptionalText(value.leadArticleTitle),
    articleCount: toPositiveInteger(value.articleCount) ?? 0,
    isPinned: value.isPinned === true,
    createdAt,
    updatedAt: toDate(value.updatedAt) ?? createdAt,
    pushedAt: toDate(value.pushedAt),
  };
}

/** 상세 응답 > 알림함 상세 (기사 묶음은 모르는 종류·빈 묶음을 버림, 기사별 대응 안내는 읽을 수 있는 것만, 형식이 틀리면 undefined) */
export function toAppNotificationDetail(response: AppNotificationDetailApiResponse): AppNotificationDetail | undefined {
  const summary = toAppNotificationSummary(response);
  if (summary === undefined) return undefined;
  const linkUrl = toOptionalText(response.linkUrl);
  return {
    ...summary,
    linkUrl: linkUrl !== undefined && WEB_LINK_PATTERN.test(linkUrl) ? linkUrl : undefined,
    articleSections: toArticleSections(response.articleSections),
    articleGuides: toArticleGuides(response.articleGuides),
  };
}

/** 최근 글 번호 응답 > 종류별 번호 (없거나 숫자가 아니면 없음) */
export function toLatestAppNotificationIds(response: LatestAppNotificationIdsApiResponse): LatestAppNotificationIds {
  return {
    latestAlertId: toPositiveInteger(response.latestAlertId),
    latestNoticeId: toPositiveInteger(response.latestNoticeId),
  };
}

function toArticleSections(value: unknown): AppNotificationArticleSection[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((section): AppNotificationArticleSection[] => {
    if (!isObject(section)) return [];
    const alertCategory = toAlertCategory(section.alertCategory);
    const articles = Array.isArray(section.articles) ? section.articles.filter(isArticleApiResponse).map(toBriefingItem) : [];
    return alertCategory === undefined || articles.length === 0 ? [] : [{ alertCategory, articles }];
  });
}

// 기사별 대응 안내 요약 (기사 번호가 없으면 버림, 건수는 음수·숫자 아님이면 0, 대응은 빈 글자를 뺌)
function toArticleGuides(value: unknown): AppNotificationArticleGuide[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((guide): AppNotificationArticleGuide[] => {
    if (!isObject(guide)) return [];
    const articleId = toPositiveInteger(guide.articleId);
    if (articleId === undefined) return [];
    const actions = Array.isArray(guide.actions)
      ? guide.actions.filter((action): action is string => typeof action === 'string' && action.trim() !== '')
      : [];
    return [
      {
        articleId: String(articleId),
        actions,
        officialCount: toPositiveInteger(guide.officialCount) ?? 0,
        factCount: toPositiveInteger(guide.factCount) ?? 0,
        newsCount: toPositiveInteger(guide.newsCount) ?? 0,
        leadOfficialItem: toResponseGuideItem(guide.leadOfficialItem),
      },
    ];
  });
}

function toAppNotificationKind(value: unknown): AppNotificationKind | undefined {
  return APP_NOTIFICATION_KINDS.find((kind) => kind === value);
}

function toAlertCategory(value: unknown): AlertCategory | undefined {
  return ALERT_CATEGORIES.find((category) => category === value);
}

// 서버가 준 순서(중요한 순)를 지키며 모르는 값·중복 제거
function toAlertCategoriesInOrder(value: unknown): AlertCategory[] {
  if (!Array.isArray(value)) return [];
  const categories = value.map(toAlertCategory).filter((category): category is AlertCategory => category !== undefined);
  return [...new Set(categories)];
}

function toPositiveInteger(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : undefined;
}

function toOptionalText(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function toDate(value: unknown): Date | undefined {
  if (typeof value !== 'string') return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
