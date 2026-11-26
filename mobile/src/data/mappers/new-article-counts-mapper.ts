import type { NewArticleCounts, TagCount } from '../models/new-article-counts';
import { isNonArrayObject } from './unknown-value-guards';

/** 오늘 새 소식 건수 응답 > 건수 (모양이 틀린 칸은 0·빈 목록, since 가 없으면 읽지 못함) */
export function toNewArticleCounts(value: unknown): NewArticleCounts | undefined {
  if (!isNonArrayObject(value)) return undefined;
  const since = toDate(value.since);
  if (since === undefined) return undefined;
  const countBySourceCode: Record<string, number> = {};
  for (const sourceCount of Array.isArray(value.sourceCounts) ? value.sourceCounts : []) {
    if (isNonArrayObject(sourceCount) && typeof sourceCount.sourceCode === 'string') {
      countBySourceCode[sourceCount.sourceCode] = toCount(sourceCount.count);
    }
  }
  return {
    since,
    lastCollectedAt: toDate(value.lastCollectedAt),
    totalCount: toCount(value.totalCount),
    exploitedCount: toCount(value.exploitedCount),
    kisaCount: toCount(value.kisaCount),
    newsCount: toCount(value.newsCount),
    fieldCounts: toTagCounts(value.fieldCounts),
    ecosystemCounts: toTagCounts(value.ecosystemCounts),
    countBySourceCode,
  };
}

function toTagCounts(value: unknown): TagCount[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((tagCount): TagCount[] => {
    if (!isNonArrayObject(tagCount) || typeof tagCount.code !== 'string' || tagCount.code.length === 0) return [];
    const sampleNames = Array.isArray(tagCount.sampleNames)
      ? tagCount.sampleNames.filter((name): name is string => typeof name === 'string' && name.length > 0)
      : [];
    return [{ code: tagCount.code, count: toCount(tagCount.count), sampleNames }];
  });
}

function toCount(value: unknown): number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : 0;
}

function toDate(value: unknown): Date | undefined {
  if (typeof value !== 'string') return undefined;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? undefined : date;
}
