import { toArticleDateLabel, toMastheadDate } from '@/core/format/briefing-date-formatter';
import type { BriefingItem, BriefingTopic } from '@/data/models/briefing-item';
import { type TodayBriefing, isTodayBriefingEmpty } from '@/data/models/today-briefing';
import type { TrendTopic } from '@/data/models/trend-topic';
import type { SbCoverKind, SbCoverTone } from '@/design';

import { AI_TRANSLATION_NOTICE_LABEL, toDisplayTitle, toMetaLabel, toPublisherLabel } from '../articles/article-row-view';
import { toTrendTopicView } from '../trend/trend-view-state';

/** 긴급 타일 표시 내용 */
export interface TodayUrgentView {
  capsLabel: string;
  title: string;
  footerLabel: string;
  coverKind: SbCoverKind;
  originalUrl: string;
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
  originalUrl: string;
}

/** 오늘 화면 전체 표시 상태 (화면은 이 값을 그대로 배치만 함) */
export interface TodayViewState {
  /** 머리 오른쪽 라벨 2줄 (날짜, 오늘 건수) */
  mastheadTrailingLines: string[];
  weeklyCount: number;
  weeklyDetail: string;
  urgent?: TodayUrgentView;
  trend?: TodayTrendView;
  streamItems: TodayStreamItemView[];
  isEmpty: boolean;
}

// 스트림 썸네일 색 조합: 순서대로 3종 반복
const THUMBNAIL_TONES: SbCoverTone[] = ['feature', 'accent', 'neutral'];
// 번역 항목 표시 꼬리 (레일·긴급 타일엔 원문 제목 줄이 없어 표시만 붙임)
const TRANSLATION_SUFFIX = ` · ${AI_TRANSLATION_NOTICE_LABEL}`;

// 소식 주제 > 커버 그림 종류 (새 3종은 기존 그림 재사용)
export const COVER_KIND_BY_TOPIC: Record<BriefingTopic, SbCoverKind> = {
  vulnerability: 'target',
  ransomware: 'lock',
  privacy: 'fingerprint',
  remoteAccess: 'terminal',
  database: 'cylinder',
  supplyChain: 'cylinder',
  policy: 'lock',
  general: 'target',
};

/** 브리핑 > 오늘 화면 표시 상태: 머리 라벨 > 긴급 > 동향 > 이번 주 건수 > 스트림 */
export function toTodayViewState(briefing: TodayBriefing, now: Date = new Date()): TodayViewState {
  return {
    mastheadTrailingLines: [toMastheadDate(briefing.date), `오늘 ${briefing.todayCount}건`],
    weeklyCount: briefing.weeklyCount,
    weeklyDetail: '이번 주에 모은 보안 소식이에요',
    urgent: briefing.urgentItem && toUrgentView(briefing.urgentItem, now),
    trend: briefing.topTrend && toTrendView(briefing.topTrend),
    streamItems: briefing.streamItems.map((item, itemIndex) => toStreamItemView(item, itemIndex, now)),
    isEmpty: isTodayBriefingEmpty(briefing),
  };
}

/** 긴급 소식 > 타일 문구 (라벨: 긴급 / CVSS 점수 또는 KEV 등재, 아래 줄: 게시자 · 날짜 (· AI 번역)) */
function toUrgentView(item: BriefingItem, now: Date): TodayUrgentView {
  return {
    capsLabel: toUrgentCapsLabel(item),
    title: toDisplayTitle(item),
    footerLabel: `${toPublisherLabel(item)} · ${toArticleDateLabel(item.publishedAt, now)}${toTranslationSuffix(item)}`,
    coverKind: COVER_KIND_BY_TOPIC[item.topic],
    originalUrl: item.originalUrl,
  };
}

function toUrgentCapsLabel(item: BriefingItem): string {
  if (item.cvssScore !== undefined) return `긴급 / CVSS ${item.cvssScore.toFixed(1)}`;
  if (item.isKev) return '긴급 / KEV 등재';
  return '긴급';
}

/** 동향 주제 > 타일 (문구는 동향 탭과 같은 원본) */
function toTrendView(trend: TrendTopic): TodayTrendView {
  const trendView = toTrendTopicView(trend);
  return { topicName: trendView.topicLabel, detail: trendView.detail };
}

/** 소식 > 스트림 항목 (게시자 · 날짜, 썸네일 색은 순서대로 3종 반복) */
function toStreamItemView(item: BriefingItem, itemIndex: number, now: Date): TodayStreamItemView {
  return {
    id: item.id,
    timeLabel: `${toMetaLabel(item, now)}${toTranslationSuffix(item)}`,
    title: toDisplayTitle(item),
    thumbnailKind: COVER_KIND_BY_TOPIC[item.topic],
    thumbnailTone: THUMBNAIL_TONES[itemIndex % THUMBNAIL_TONES.length],
    originalUrl: item.originalUrl,
  };
}

function toTranslationSuffix(item: BriefingItem): string {
  return item.translatedTitle !== undefined ? TRANSLATION_SUFFIX : '';
}
