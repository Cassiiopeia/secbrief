import { toArticleDateLabel, toMastheadDate } from '@/core/format/briefing-date-formatter';
import type { BriefingItem } from '@/data/models/briefing-item';
import { type TodayBriefing, isTodayBriefingEmpty } from '@/data/models/today-briefing';
import type { TrendTopic } from '@/data/models/trend-topic';
import type { SbCoverKind, SbShortcut } from '@/design';

import { toArticleImageUrl, toTopicCoverKind } from '../articles/article-cover';
import {
  type ArticleRowView,
  toArticleRowView,
  toCompactMetaLabel,
  toDisplayTitle,
  toPublisherLabel,
  toTopicLabel,
  toTranslationSuffix,
} from '../articles/article-row-view';
import { BROWSABLE_TOPICS, toTopicBrowseKey } from '../browse/browse-target';
import { toTrendTopicView } from '../trend/trend-view-state';
import { selectTodayHeadlines } from './today-headline-selection';

/** 긴급 타일 표시 내용 */
export interface TodayUrgentView {
  capsLabel: string;
  title: string;
  footerLabel: string;
  /** 대표 이미지 주소 (없으면 글만) */
  coverImageUrl?: string;
  /** 누르면 열 기사 (상세 화면이 먼저 그릴 표시 내용) */
  article: ArticleRowView;
}

/** 동향 1위 타일 표시 내용 */
export interface TodayTrendView {
  topicName: string;
  detail: string;
  /** 타일 배경에 깔리는 주제 대표 그림 */
  coverKind: SbCoverKind;
}

/** 스트림 항목 표시 내용 */
export interface TodayStreamItemView {
  id: string;
  timeLabel: string;
  title: string;
  /** 대표 이미지 주소 (없으면 글만) */
  thumbnailImageUrl?: string;
  /** 누르면 열 기사 (상세 화면이 먼저 그릴 표시 내용) */
  article: ArticleRowView;
}

/** 주요 소식 구역 표시 내용 (가로 카드 레일) */
export interface TodayHeadlineView {
  title: string;
  caption: string;
  /** AI 가 골랐으면 제목 앞에 AI 표시 */
  isAiPicked: boolean;
  cards: ArticleRowView[];
}

/** 오늘 화면 전체 표시 상태 (화면은 이 값을 그대로 배치만 함) */
export interface TodayViewState {
  /** 머리 오른쪽 라벨 2줄 (날짜, 오늘 건수) */
  mastheadTrailingLines: string[];
  todayCount: number;
  weeklyCount: number;
  /** 주요 소식 (AI 가 고른 것, 없으면 대체 규칙. 그래도 없으면 undefined) */
  headline?: TodayHeadlineView;
  topicShortcuts: SbShortcut[];
  weeklyDetail: string;
  urgent?: TodayUrgentView;
  trend?: TodayTrendView;
  streamItems: TodayStreamItemView[];
  isEmpty: boolean;
}

// 주요 소식 구역 문구 2종: AI 가 고른 경우 / 대체 규칙으로 채운 경우
const AI_PICKED_HEADLINE_TEXTS = { title: 'AI 가 고른 주요 소식', caption: '제목을 보고 AI 가 골랐어요. 정확한 내용은 원문에서 확인해 주세요.' };
const FALLBACK_HEADLINE_TEXTS = { title: '지금 주목할 소식', caption: '악용 중인 취약점과 새로 올라온 소식이에요.' };

/** 브리핑 > 오늘 화면 표시 상태: 머리 라벨 > 주요 소식 > 긴급 > 동향 > 건수 > 주제 바로가기 > 스트림 */
export function toTodayViewState(briefing: TodayBriefing, now: Date = new Date()): TodayViewState {
  const selection = selectTodayHeadlines(briefing);
  return {
    mastheadTrailingLines: [toMastheadDate(briefing.date), `오늘 ${briefing.todayCount}건`],
    todayCount: briefing.todayCount,
    headline:
      selection.headlineItems.length === 0
        ? undefined
        : {
            ...(selection.isAiPicked ? AI_PICKED_HEADLINE_TEXTS : FALLBACK_HEADLINE_TEXTS),
            isAiPicked: selection.isAiPicked,
            cards: selection.headlineItems.map((item) => toArticleRowView(item, now)),
          },
    topicShortcuts: toTopicShortcuts(),
    weeklyCount: briefing.weeklyCount,
    weeklyDetail: '이번 주에 모은 소식',
    urgent: briefing.urgentItem && toUrgentView(briefing.urgentItem, now),
    trend: briefing.topTrend && toTrendView(briefing.topTrend),
    streamItems: selection.streamItems.map((item) => toStreamItemView(item, now)),
    isEmpty: isTodayBriefingEmpty(briefing),
  };
}

/** 긴급 소식 > 타일 문구 (라벨: 긴급 / CVSS 점수 또는 KEV 등재, 아래 줄: 게시자 · 날짜 (· AI 번역)) */
function toUrgentView(item: BriefingItem, now: Date): TodayUrgentView {
  return {
    capsLabel: toUrgentCapsLabel(item),
    title: toDisplayTitle(item),
    footerLabel: `${toPublisherLabel(item)} · ${toArticleDateLabel(item.publishedAt, now)}${toTranslationSuffix(item)}`,
    coverImageUrl: toArticleImageUrl(item),
    article: toArticleRowView(item, now),
  };
}

/** 주제 바로가기 8개 (일반 제외): 주제 이름 + 주제 대표 커버 그림 */
export function toTopicShortcuts(): SbShortcut[] {
  return BROWSABLE_TOPICS.map((topic) => ({
    key: toTopicBrowseKey(topic),
    label: toTopicLabel(topic),
    coverKind: toTopicCoverKind(topic),
    accessibilityLabel: `${toTopicLabel(topic)} 소식 모아 보기`,
  }));
}

function toUrgentCapsLabel(item: BriefingItem): string {
  if (item.cvssScore !== undefined) return `긴급 / CVSS ${item.cvssScore.toFixed(1)}`;
  if (item.isKev) return '긴급 / KEV 등재';
  return '긴급';
}

/** 동향 주제 > 타일 (문구는 탐색 탭 주간 동향과 같은 원본) */
function toTrendView(trend: TrendTopic): TodayTrendView {
  const trendView = toTrendTopicView(trend);
  return { topicName: trendView.topicLabel, detail: trendView.detail, coverKind: toTopicCoverKind(trend.topic) };
}

/** 소식 > 스트림 항목 (게시자 · 짧은 날짜 (· AI 번역), 대표 이미지 있을 때만 썸네일) */
function toStreamItemView(item: BriefingItem, now: Date): TodayStreamItemView {
  return {
    id: item.id,
    timeLabel: toCompactMetaLabel(item, now),
    title: toDisplayTitle(item),
    thumbnailImageUrl: toArticleImageUrl(item),
    article: toArticleRowView(item, now),
  };
}
