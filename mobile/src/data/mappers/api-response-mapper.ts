import type { ArticleImportance, ArticleKind, ArticleRegion, BriefingItem, BriefingTopic } from '../models/briefing-item';
import type { ArticlePage } from '../models/article-page';
import type { ArticleDetail } from '../models/article-detail';
import type { ArticleSearchMode, ArticleSearchResult } from '../models/article-search-result';
import type { CollectSourceStatus } from '../models/collect-source-status';
import type { TodayBriefing } from '../models/today-briefing';
import type { TrendingKeyword } from '../models/trending-keyword';
import type { TrendTopic, WeeklyTrend } from '../models/trend-topic';
import { type ArticleBriefApiResponse, toArticleBrief, toNonBlankText } from './article-brief-mapper';
import { toResponseGuideSections } from './response-guide-mapper';

/** 서버 기사 응답 (enum 은 대문자 이름) */
export interface ArticleApiResponse {
  id: number;
  kind: string;
  region: string;
  topic: string;
  /** 아래 4개는 나중에 생긴 필드: 이전 서버는 아예 안 줌 */
  keywords?: string[] | null;
  importance?: string | null;
  subjectName?: string | null;
  sourceCode?: string | null;
  title: string;
  translatedTitle: string | null;
  /** AI 한 줄 결론. 없으면 null, 이 필드가 생기기 전 서버는 아예 안 줌 */
  oneLine?: string | null;
  originalUrl: string;
  /** 대표 이미지 경로. 이미지가 없으면 null, 이 필드가 생기기 전 서버는 아예 안 줌 */
  imagePath?: string | null;
  publisherName: string;
  publishedAt: string;
  isPublishedAtEstimated: boolean;
  cveId: string | null;
  cvssScore: number | null;
  cvssVersion: string | null;
  isKev: boolean;
  isUrgent: boolean;
  /** 분류 태그 (#93). 이 필드가 생기기 전 서버는 아예 안 줌 */
  fieldCode?: string | null;
  ecosystemCode?: string | null;
  packageName?: string | null;
  productCode?: string | null;
  isNotable?: boolean | null;
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
  /** 나중에 생긴 필드: 이전 서버는 아예 안 줌 */
  headlineItems?: ArticleApiResponse[] | null;
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

export interface TrendingKeywordApiResponse {
  keyword: string;
  articleCount: number;
  /** 아래 3개는 나중에 생긴 필드. rankChange: 양수 상승, 음수 하락, 0 유지, null 신규 */
  previousArticleCount?: number | null;
  rank?: number | null;
  rankChange?: number | null;
}

export interface TrendingKeywordsApiResponse {
  keywords: TrendingKeywordApiResponse[];
}

export interface ArticleDetailApiResponse {
  article: ArticleApiResponse;
  summary?: string | null;
  keyPoints?: string[] | null;
  actionGuide?: string | null;
  isAiSummarized?: boolean | null;
  /** AI 브리핑. 이전 방식 요약만 있으면 null, 이 필드가 생기기 전 서버는 아예 안 줌 */
  brief?: ArticleBriefApiResponse | null;
  facts?: { label: string; value: string }[] | null;
  relatedItems?: ArticleApiResponse[] | null;
  /** 대응 모아보기 (#71). 이 필드가 생기기 전 서버는 아예 안 줌 (모양은 response-guide-mapper 가 확인) */
  responseGuideSections?: unknown;
}

export interface ArticleSearchApiResponse {
  items: ArticleApiResponse[];
  searchMode: string;
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
  EVENT: 'event',
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
const IMPORTANCE_BY_API_NAME: Record<string, ArticleImportance> = {
  HEADLINE: 'headline',
  NOTABLE: 'notable',
  ROUTINE: 'routine',
};
// 서버 SearchMode.SEMANTIC (그 밖·모르는 값은 글자 일치 검색으로 봄)
const SEMANTIC_SEARCH_MODE = 'SEMANTIC';
// 수집 성공 상태 이름 (서버 CollectStatus.SUCCESS)
const COLLECT_SUCCESS_STATUS = 'SUCCESS';

/** 기사 응답 > 소식 (모르는 주제 general, 모르는 종류 news, 모르는 지역 overseas, 모르는·없는 중요도 routine, null > undefined) */
export function toBriefingItem(response: ArticleApiResponse): BriefingItem {
  return {
    id: String(response.id),
    kind: KIND_BY_API_NAME[response.kind] ?? 'news',
    region: REGION_BY_API_NAME[response.region] ?? 'overseas',
    topic: TOPIC_BY_API_NAME[response.topic] ?? 'general',
    importance: IMPORTANCE_BY_API_NAME[response.importance ?? ''] ?? 'routine',
    subjectName: response.subjectName ? response.subjectName : undefined,
    sourceCode: response.sourceCode ? response.sourceCode : undefined,
    keywords: response.keywords ?? [],
    title: response.title,
    translatedTitle: response.translatedTitle ? response.translatedTitle : undefined,
    oneLine: toNonBlankText(response.oneLine),
    originalUrl: response.originalUrl,
    imagePath: response.imagePath ? response.imagePath : undefined,
    publisherName: response.publisherName,
    publishedAt: new Date(response.publishedAt),
    isPublishedAtEstimated: response.isPublishedAtEstimated,
    cveId: response.cveId ?? undefined,
    cvssScore: response.cvssScore ?? undefined,
    cvssVersion: response.cvssVersion ?? undefined,
    isKev: response.isKev,
    isUrgent: response.isUrgent,
    fieldCode: toNonBlankText(response.fieldCode),
    ecosystemCode: toNonBlankText(response.ecosystemCode),
    packageName: toNonBlankText(response.packageName),
    productCode: toNonBlankText(response.productCode),
    isNotable: response.isNotable === true,
  };
}

/** 기사 응답으로 읽을 수 있는 최소 모양 (번호·제목·원문 주소·게시일): 모양을 믿지 않는 응답(알림함·대응 모아보기)용 */
export function isArticleApiResponse(value: unknown): value is ArticleApiResponse {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.id === 'number' &&
    typeof candidate.title === 'string' &&
    typeof candidate.originalUrl === 'string' &&
    typeof candidate.publishedAt === 'string' &&
    !Number.isNaN(new Date(candidate.publishedAt).getTime())
  );
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
    headlineItems: (response.headlineItems ?? []).map(toBriefingItem),
    streamItems: response.streamItems.map(toBriefingItem),
  };
}

export function toWeeklyTrend(response: WeeklyTrendApiResponse): WeeklyTrend {
  return { weekStartDate: toLocalDate(response.weekStartDate), topics: response.topics.map(toTrendTopic) };
}

export function toArticlePage(response: ArticlePageApiResponse): ArticlePage {
  return { items: response.items.map(toBriefingItem), nextCursor: response.nextCursor ?? undefined };
}

/** 키워드 순위 응답 > 키워드 (순위가 없으면 받은 순서, rankChange 가 null 이면 신규, 필드가 없으면 변화 모름) */
export function toTrendingKeywords(response: TrendingKeywordsApiResponse): TrendingKeyword[] {
  return response.keywords.map((keywordResponse, keywordIndex) => ({
    keyword: keywordResponse.keyword,
    articleCount: keywordResponse.articleCount,
    rank: keywordResponse.rank ?? keywordIndex + 1,
    isNew: keywordResponse.rankChange === null,
    rankChange: keywordResponse.rankChange ?? undefined,
  }));
}

/** 기사 상세 응답 > 상세 (요약이 아직 없으면 summary 없음·keyPoints 빈 배열, 없는 목록은 빈 배열, 브리핑·대응 모아보기는 읽을 수 있을 때만) */
export function toArticleDetail(response: ArticleDetailApiResponse): ArticleDetail {
  const article = toBriefingItem(response.article);
  return {
    article,
    brief: toArticleBrief(response.brief, article.oneLine),
    summary: response.summary ? response.summary : undefined,
    keyPoints: response.keyPoints ?? [],
    actionGuide: response.actionGuide ? response.actionGuide : undefined,
    isAiSummarized: response.isAiSummarized === true,
    facts: (response.facts ?? []).map(({ label, value }) => ({ label, value })),
    relatedItems: (response.relatedItems ?? []).map(toBriefingItem),
    responseGuideSections: toResponseGuideSections(response.responseGuideSections),
  };
}

export function toArticleSearchResult(response: ArticleSearchApiResponse): ArticleSearchResult {
  const searchMode: ArticleSearchMode = response.searchMode === SEMANTIC_SEARCH_MODE ? 'semantic' : 'keyword';
  return { items: response.items.map(toBriefingItem), searchMode };
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

export function toApiImportance(importance: ArticleImportance): string {
  return toApiName(IMPORTANCE_BY_API_NAME, importance);
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
