import { toMastheadDate } from '@/core/format/briefing-date-formatter';
import type { BriefingItem } from '@/data/models/briefing-item';

import { type ArticleRowView, toArticleRowView } from '../articles/article-row-view';

/** 타임라인 날짜 묶음: 그날 게시된 행사 소식들 */
export interface EventTimelineGroup {
  /** 묶음 구분 값 (예: 2026-10-26, 기기 시간대 날짜) */
  dateKey: string;
  /** 날짜 머리 (예: 11.26 THU) */
  dateLabel: string;
  articles: ArticleRowView[];
}

/**
 * 행사 소식 > 날짜별 타임라인 (받은 순서 = 최신순, 위에서 maximumCount 건)
 * 날짜는 기사 게시일 기준 (행사가 열리는 날짜를 추정하지 않음)
 */
export function toEventTimelineGroups(items: BriefingItem[], now: Date, maximumCount: number): EventTimelineGroup[] {
  const groups: EventTimelineGroup[] = [];
  for (const item of items.slice(0, maximumCount)) {
    const dateKey = `${item.publishedAt.getFullYear()}-${item.publishedAt.getMonth()}-${item.publishedAt.getDate()}`;
    const lastGroup = groups[groups.length - 1];
    if (lastGroup?.dateKey === dateKey) {
      lastGroup.articles.push(toArticleRowView(item, now));
    } else {
      groups.push({ dateKey, dateLabel: toMastheadDate(item.publishedAt), articles: [toArticleRowView(item, now)] });
    }
  }
  return groups;
}
