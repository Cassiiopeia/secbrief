import type { BriefingItem } from '@/data/models/briefing-item';
import type { TodayBriefing } from '@/data/models/today-briefing';

// 주요 소식 구역 최대 건수
export const HEADLINE_MAXIMUM_COUNT = 6;
// 대체 규칙으로 채울 때: 구역을 만드는 최소 건수, 스트림에 남겨 둘 최소 건수
const FALLBACK_HEADLINE_MINIMUM_COUNT = 2;
const FALLBACK_STREAM_MINIMUM_COUNT = 3;

/** 오늘 탭 소식 나누기 결과: 주요 소식 구역 + 최신 스트림 (같은 소식이 두 구역에 나오지 않음) */
export interface TodayHeadlineSelection {
  headlineItems: BriefingItem[];
  streamItems: BriefingItem[];
  /** AI 가 고른 소식인지 (false 면 대체 규칙으로 채움) */
  isAiPicked: boolean;
}

/**
 * 주요 소식 고르기: AI 가 고른 큰 소식(routine 제외) > 없으면 대체 규칙. 긴급 타일에 나온 소식은 어느 쪽에서도 뺌
 * 대체 규칙: 스트림에서 악용 중(KEV) > 뉴스·공지 > 그 밖 순으로 뽑되 스트림에 3건은 남김, 2건 미만이면 구역 없음
 */
export function selectTodayHeadlines(briefing: TodayBriefing): TodayHeadlineSelection {
  const urgentItemId = briefing.urgentItem?.id;
  const streamItems = toUniqueItems(briefing.streamItems).filter((item) => item.id !== urgentItemId);

  const aiPickedItems = toUniqueItems(briefing.headlineItems)
    .filter((item) => item.importance !== 'routine' && item.id !== urgentItemId)
    .slice(0, HEADLINE_MAXIMUM_COUNT);
  if (aiPickedItems.length > 0) {
    return { headlineItems: aiPickedItems, streamItems: toItemsWithout(streamItems, aiPickedItems), isAiPicked: true };
  }

  const fallbackCount = Math.min(HEADLINE_MAXIMUM_COUNT, streamItems.length - FALLBACK_STREAM_MINIMUM_COUNT);
  if (fallbackCount < FALLBACK_HEADLINE_MINIMUM_COUNT) return { headlineItems: [], streamItems, isAiPicked: false };
  const fallbackItems = toFallbackOrder(streamItems).slice(0, fallbackCount);
  return { headlineItems: fallbackItems, streamItems: toItemsWithout(streamItems, fallbackItems), isAiPicked: false };
}

// 대체 순서: 악용 중(KEV) > 뉴스·공지 > 그 밖 (같은 묶음 안에서는 받은 순서 = 최신순)
function toFallbackOrder(items: BriefingItem[]): BriefingItem[] {
  const toFallbackRank = (item: BriefingItem): number => (item.isKev ? 0 : item.kind !== 'vulnerability' ? 1 : 2);
  return items
    .map((item, itemIndex) => ({ item, itemIndex }))
    .sort((left, right) => toFallbackRank(left.item) - toFallbackRank(right.item) || left.itemIndex - right.itemIndex)
    .map(({ item }) => item);
}

function toUniqueItems(items: BriefingItem[]): BriefingItem[] {
  return items.filter((item, itemIndex) => items.findIndex((candidate) => candidate.id === item.id) === itemIndex);
}

function toItemsWithout(items: BriefingItem[], excludedItems: BriefingItem[]): BriefingItem[] {
  const excludedIds = new Set(excludedItems.map((item) => item.id));
  return items.filter((item) => !excludedIds.has(item.id));
}
