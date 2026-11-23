import { toClockTime, toMastheadDate } from '@/core/format/briefing-date-formatter';
import type { BriefingItem, BriefingTopic } from '@/data/models/briefing-item';
import { type TodayBriefing, isTodayBriefingEmpty } from '@/data/models/today-briefing';
import type { TrendTopic } from '@/data/models/trend-topic';
import type { SbCoverKind, SbCoverTone } from '@/design';

/** 긴급 타일 표시 내용 */
export interface TodayUrgentView {
  capsLabel: string;
  title: string;
  footerLabel: string;
  coverKind: SbCoverKind;
}

/** 동향 1위 타일 표시 내용 */
export interface TodayTrendView {
  topicName: string;
  detail: string;
}

/** 스트림 항목 표시 내용 */
export interface TodayStreamItemView {
  id: string;
  timeLabel: string;
  title: string;
  thumbnailKind: SbCoverKind;
  thumbnailTone: SbCoverTone;
}

/** 오늘 화면 전체 표시 상태 (화면은 이 값을 그대로 배치만 함) */
export interface TodayViewState {
  /** 머리 오른쪽 라벨 2줄 (날짜, 읽은 건수) */
  mastheadTrailingLines: string[];
  streakDays: number;
  streakDetail: string;
  urgent?: TodayUrgentView;
  trend?: TodayTrendView;
  streamItems: TodayStreamItemView[];
  isEmpty: boolean;
}

// 스트림 썸네일 색 조합: 순서대로 3종 반복
const THUMBNAIL_TONES: SbCoverTone[] = ['feature', 'accent', 'neutral'];

// 소식 주제 > 커버 그림 종류
const COVER_KIND_BY_TOPIC: Record<BriefingTopic, SbCoverKind> = {
  vulnerability: 'target',
  ransomware: 'lock',
  privacy: 'fingerprint',
  remoteAccess: 'terminal',
  database: 'cylinder',
  supplyChain: 'cylinder',
  policy: 'lock',
  general: 'target',
};

/** 브리핑 > 오늘 화면 표시 상태: 머리 라벨 > 긴급 > 동향 > 연속 확인 > 스트림 */
export function toTodayViewState(briefing: TodayBriefing): TodayViewState {
  return {
    mastheadTrailingLines: [toMastheadDate(briefing.date), `오늘 ${briefing.todayCount}건`],
    streakDays: briefing.weeklyCount,
    streakDetail: '이번 주에 모은 보안 소식이에요',
    urgent: briefing.urgentItem && toUrgentView(briefing.urgentItem),
    trend: briefing.topTrend && toTrendView(briefing.topTrend),
    streamItems: briefing.streamItems.map(toStreamItemView),
    isEmpty: isTodayBriefingEmpty(briefing),
  };
}

/** 긴급 소식 > 타일 문구 (라벨: 긴급 / CVSS 점수, 아래 줄: 제품 · 내 스택 해당) */
function toUrgentView(item: BriefingItem): TodayUrgentView {
  const footerParts = [item.publisherName];
  return {
    capsLabel: item.cvssScore === undefined ? '긴급' : `긴급 / CVSS ${item.cvssScore.toFixed(1)}`,
    title: item.title,
    footerLabel: footerParts.join(' · '),
    coverKind: COVER_KIND_BY_TOPIC[item.topic],
  };
}

/** 동향 주제 > 타일 설명 (늘었어요 / 줄었어요 / 같아요) */
function toTrendView(trend: TrendTopic): TodayTrendView {
  const changePercent = trend.changePercent ?? 0;
  let changeText = '지난주와 같아요';
  if (changePercent > 0) changeText = `지난주보다 ${changePercent}% 늘었어요`;
  if (changePercent < 0) changeText = `지난주보다 ${-changePercent}% 줄었어요`;
  return { topicName: trend.topic, detail: `${trend.weeklyCount}건 · ${changeText}` };
}

/** 소식 > 스트림 항목 (시간 · 출처, 썸네일 색은 순서대로 3종 반복) */
function toStreamItemView(item: BriefingItem, itemIndex: number): TodayStreamItemView {
  return {
    id: item.id,
    timeLabel: `${toClockTime(item.publishedAt)} · ${item.publisherName}`,
    title: item.title,
    thumbnailKind: COVER_KIND_BY_TOPIC[item.topic],
    thumbnailTone: THUMBNAIL_TONES[itemIndex % THUMBNAIL_TONES.length],
  };
}
