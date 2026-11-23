import type { ArticleKind, ArticleRegion, BriefingItem, BriefingTopic } from '../models/briefing-item';
import type { ArticlePage } from '../models/article-page';
import type { CollectSourceStatus } from '../models/collect-source-status';
import type { TodayBriefing } from '../models/today-briefing';
import type { TrendTopic, WeeklyTrend } from '../models/trend-topic';

/** 서버 기사 응답 (enum 은 대문자 이름) */
export interface ArticleApiResponse {
  id: number;
  kind: string;
  region: string;
  topic: string;
  title: string;
  translatedTitle: string | null;
  originalUrl: string;
  publisherName: string;
  publishedAt: string;
  isPublishedAtEstimated: boolean;
  cveId: string | null;
  cvssScore: number | null;
  cvssVersion: string | null;
  isKev: boolean;
  isUrgent: boolean;
}

export interface TrendTopicApiResponse {
  topic: string;
  weeklyCount: number;
  previousWeekCount: number;
  changePercent: number | null;
}

export interface TodayApiResponse {
  date: string;
  urgentItem: ArticleApiResponse | null;
  todayCount: number;
  weeklyCount: number;
  topTrend: TrendTopicApiResponse | null;
  streamItems: ArticleApiResponse[];
}

export interface WeeklyTrendApiResponse {
  weekStartDate: string;
  topics: TrendTopicApiResponse[];
}

export interface ArticlePageApiResponse {
  items: ArticleApiResponse[];
  nextCursor: string | null;
}

export interface SourceStatusApiResponse {
  code: string;
  displayName: string;
  kind: string;
  region: string;
  lastCollectedAt: string | null;
  lastStatus: string | null;
}

// 서버 enum 이름 <-> 앱 값 (단일 원본)
const TOPIC_BY_API_NAME: Record<string, BriefingTopic> = {
  VULNERABILITY: 'vulnerability',
  RANSOMWARE: 'ransomware',
  PRIVACY: 'privacy',
  REMOTE_ACCESS: 'remoteAccess',
  DATABASE: 'database',
  SUPPLY_CHAIN: 'supplyChain',
  POLICY: 'policy',
  GENERAL: 'general',
};
const KIND_BY_API_NAME: Record<string, ArticleKind> = {
  NEWS: 'news',
  ADVISORY: 'advisory',
  VULNERABILITY: 'vulnerability',
};
const REGION_BY_API_NAME: Record<string, ArticleRegion> = {
  DOMESTIC: 'domestic',
  OVERSEAS: 'overseas',
};
// 수집 성공 상태 이름 (서버 CollectStatus.SUCCESS)
const COLLECT_SUCCESS_STATUS = 'SUCCESS';

/** 기사 응답 > 소식 (모르는 주제 general, 모르는 종류 news, 모르는 지역 overseas, null > undefined) */
export function toBriefingItem(response: ArticleApiResponse): BriefingItem {
  return {
    id: String(response.id),
    kind: KIND_BY_API_NAME[response.kind] ?? 'news',
    region: REGION_BY_API_NAME[response.region] ?? 'overseas',
    topic: TOPIC_BY_API_NAME[response.topic] ?? 'general',
    title: response.title,
    translatedTitle: response.translatedTitle ? response.translatedTitle : undefined,
    originalUrl: response.originalUrl,
    publisherName: response.publisherName,
    publishedAt: new Date(response.publishedAt),
    isPublishedAtEstimated: response.isPublishedAtEstimated,
    cveId: response.cveId ?? undefined,
    cvssScore: response.cvssScore ?? undefined,
    cvssVersion: response.cvssVersion ?? undefined,
    isKev: response.isKev,
    isUrgent: response.isUrgent,
  };
}

export function toTrendTopic(response: TrendTopicApiResponse): TrendTopic {
  return {
    topic: TOPIC_BY_API_NAME[response.topic] ?? 'general',
    weeklyCount: response.weeklyCount,
    previousWeekCount: response.previousWeekCount,
    changePercent: response.changePercent ?? undefined,
  };
}

export function toTodayBriefing(response: TodayApiResponse): TodayBriefing {
  return {
    date: toLocalDate(response.date),
    todayCount: response.todayCount,
    weeklyCount: response.weeklyCount,
    urgentItem: response.urgentItem ? toBriefingItem(response.urgentItem) : undefined,
    topTrend: response.topTrend ? toTrendTopic(response.topTrend) : undefined,
    streamItems: response.streamItems.map(toBriefingItem),
  };
}

export function toWeeklyTrend(response: WeeklyTrendApiResponse): WeeklyTrend {
  return { weekStartDate: toLocalDate(response.weekStartDate), topics: response.topics.map(toTrendTopic) };
}

export function toArticlePage(response: ArticlePageApiResponse): ArticlePage {
  return { items: response.items.map(toBriefingItem), nextCursor: response.nextCursor ?? undefined };
}

export function toCollectSourceStatus(response: SourceStatusApiResponse): CollectSourceStatus {
  return {
    code: response.code,
    displayName: response.displayName,
    kind: KIND_BY_API_NAME[response.kind] ?? 'news',
    region: REGION_BY_API_NAME[response.region] ?? 'overseas',
    lastCollectedAt: response.lastCollectedAt ? new Date(response.lastCollectedAt) : undefined,
    isLastCollectSucceeded: response.lastStatus === COLLECT_SUCCESS_STATUS,
  };
}

/** 앱 주제 > 서버 enum 이름 (supplyChain > SUPPLY_CHAIN) */
export function toApiTopic(topic: BriefingTopic): string {
  return toApiName(TOPIC_BY_API_NAME, topic);
}

export function toApiKind(kind: ArticleKind): string {
  return toApiName(KIND_BY_API_NAME, kind);
}

export function toApiRegion(region: ArticleRegion): string {
  return toApiName(REGION_BY_API_NAME, region);
}

function toApiName<Value extends string>(valueByApiName: Record<string, Value>, value: Value): string {
  const apiName = Object.keys(valueByApiName).find((name) => valueByApiName[name] === value);
  if (apiName === undefined) throw new Error(`unknown api value: ${value}`);
  return apiName;
}

// 서버 날짜(서울 기준 yyyy-MM-dd) > 기기 시간대 그날 00:00 (요일·날짜 표기용)
function toLocalDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day);
}
