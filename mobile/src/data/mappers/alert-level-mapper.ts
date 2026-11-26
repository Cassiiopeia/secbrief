import {
  type AlertLevelChange,
  type AlertLevelChangeDirection,
  type AlertLevelDashboard,
  type AlertLevelStatus,
  type CyberCrisisLevel,
  type InternationalIndicator,
  type InternationalIndicatorCode,
  type WeeklyThreatCounts,
} from '../models/alert-level-dashboard';
import { isNonArrayObject } from './unknown-value-guards';

/** 서버 경보단계 화면 응답 (모든 칸을 믿지 않고 읽음: 서버가 칸을 빼거나 더할 수 있음) */
export interface AlertLevelDashboardApiResponse {
  current?: unknown;
  changes?: unknown;
  weeklyCounts?: unknown;
  internationalIndicators?: unknown;
  sourceUrl?: unknown;
}

// 서버 enum 이름 > 앱 값 (CyberCrisisLevel, AlertLevelChangeDirection, InternationalAlertIndicator: server-contract.test.ts 가 비교)
export const CYBER_CRISIS_LEVEL_BY_API_NAME: Record<string, CyberCrisisLevel> = {
  NORMAL: 'normal',
  ATTENTION: 'attention',
  CAUTION: 'caution',
  ALERT: 'alert',
  SEVERE: 'severe',
};
export const ALERT_LEVEL_DIRECTION_BY_API_NAME: Record<string, AlertLevelChangeDirection> = {
  RAISED: 'raised',
  LOWERED: 'lowered',
  KEPT: 'kept',
};
export const INTERNATIONAL_INDICATOR_CODE_BY_API_NAME: Record<string, InternationalIndicatorCode> = {
  CIS_MS_ISAC: 'cisMsIsac',
  SANS_ISC_INFOCON: 'sansIscInfocon',
};
// 원문 링크로 여는 주소 모양 (http·https 만)
const WEB_LINK_PATTERN = /^https?:\/\//i;

/** 응답 > 경보단계 화면 데이터 (형식이 틀린 줄은 버림) */
export function toAlertLevelDashboard(response: AlertLevelDashboardApiResponse): AlertLevelDashboard {
  return {
    current: toAlertLevelStatus(response.current),
    changes: toList(response.changes, toAlertLevelChange),
    weeklyCounts: toWeeklyThreatCounts(response.weeklyCounts),
    internationalIndicators: toList(response.internationalIndicators, toInternationalIndicator),
    sourceUrl: toWebLink(response.sourceUrl),
  };
}

function toAlertLevelStatus(value: unknown): AlertLevelStatus | undefined {
  if (!isNonArrayObject(value)) return undefined;
  const since = toDate(value.since);
  const articleId = toIdText(value.articleId);
  const originalUrl = toWebLink(value.originalUrl);
  if (since === undefined || articleId === undefined || typeof value.title !== 'string' || originalUrl === undefined) {
    return undefined;
  }
  return { level: toLevel(value.level), since, articleId, title: value.title, originalUrl };
}

function toAlertLevelChange(value: unknown): AlertLevelChange | undefined {
  if (!isNonArrayObject(value)) return undefined;
  const publishedAt = toDate(value.publishedAt);
  const articleId = toIdText(value.articleId);
  const originalUrl = toWebLink(value.originalUrl);
  if (publishedAt === undefined || articleId === undefined || typeof value.title !== 'string' || originalUrl === undefined) {
    return undefined;
  }
  return {
    articleId,
    fromLevel: toLevel(value.fromLevel),
    toLevel: toLevel(value.toLevel),
    direction: typeof value.direction === 'string' ? ALERT_LEVEL_DIRECTION_BY_API_NAME[value.direction] : undefined,
    publishedAt,
    title: value.title,
    originalUrl,
  };
}

function toWeeklyThreatCounts(value: unknown): WeeklyThreatCounts | undefined {
  if (!isNonArrayObject(value)) return undefined;
  const { exploitedVulnerabilityCount, urgentArticleCount, kisaNoticeCount } = value;
  if (!isCount(exploitedVulnerabilityCount) || !isCount(urgentArticleCount) || !isCount(kisaNoticeCount)) return undefined;
  return { exploitedVulnerabilityCount, urgentArticleCount, kisaNoticeCount };
}

function toInternationalIndicator(value: unknown): InternationalIndicator | undefined {
  if (!isNonArrayObject(value) || typeof value.code !== 'string') return undefined;
  const code = INTERNATIONAL_INDICATOR_CODE_BY_API_NAME[value.code];
  const sourceUrl = toWebLink(value.sourceUrl);
  if (code === undefined || sourceUrl === undefined || !isCount(value.levelCount)) return undefined;
  const isLevelRead = typeof value.level === 'string' && isCount(value.levelStep) && value.levelStep > 0;
  return {
    code,
    level: isLevelRead ? (value.level as string) : undefined,
    levelStep: isLevelRead ? (value.levelStep as number) : 0,
    levelCount: value.levelCount,
    checkedAt: toDate(value.checkedAt),
    sourceUrl,
  };
}

function toList<Item>(value: unknown, toItem: (item: unknown) => Item | undefined): Item[] {
  return Array.isArray(value) ? value.map(toItem).filter((item): item is Item => item !== undefined) : [];
}

function toLevel(value: unknown): CyberCrisisLevel | undefined {
  return typeof value === 'string' ? CYBER_CRISIS_LEVEL_BY_API_NAME[value] : undefined;
}

function toIdText(value: unknown): string | undefined {
  return typeof value === 'number' && Number.isInteger(value) ? String(value) : undefined;
}

function toWebLink(value: unknown): string | undefined {
  return typeof value === 'string' && WEB_LINK_PATTERN.test(value) ? value : undefined;
}

function toDate(value: unknown): Date | undefined {
  if (typeof value !== 'string') return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}

function isCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0;
}
