import { toAbsoluteDate, toClockTime, toRelativeTime } from '@/core/format/briefing-date-formatter';
import { toDayGroupLabel, toLocalDayKey } from '@/core/format/notification-date-formatter';
import type { AppNotificationKind, AppNotificationSummary, InboxNotificationKind } from '@/data/models/app-notification';
import type { SbBadgeContent } from '@/design';

import { ALERT_CATEGORY_TEXTS } from './alert-category-texts';
import { type AppNotificationNewnessBaseline, isNotificationNew } from './notification-read-state';

/** 알림함 한 줄 표시 내용 (긴급 알림·공지사항 공통 틀) */
export interface NotificationInboxRowView {
  id: number;
  kind: AppNotificationKind;
  /** 알림 종류 배지 (공지사항은 없음) */
  badges: SbBadgeContent[];
  /** 공지사항 고정 표시 */
  isPinned: boolean;
  title: string;
  /** 긴급 알림: 대표 기사 제목 (+ 외 N건), 공지사항: 본문 앞부분 */
  detail: string;
  /** 오늘 묶음 상대 표기(3시간 전), 그 밖 시각(22:51), 공지사항 날짜(2026.11.27) */
  timeLabel: string;
  isNew: boolean;
  accessibilityLabel: string;
}

/** 날짜 묶음 한 개 (SectionList 구역) */
export interface NotificationInboxSection {
  key: string;
  /** 묶음 이름 (공지사항은 묶지 않아 없음) */
  title?: string;
  data: NotificationInboxRowView[];
}

const NEW_ACCESSIBILITY_PREFIX = '새 알림';
const PINNED_ACCESSIBILITY_TEXT = '고정된 공지';

/** 목록 한 줄 > 표시 내용 (새 글 판정 포함) */
export function toNotificationInboxRowView(
  summary: AppNotificationSummary,
  baseline: AppNotificationNewnessBaseline,
  now: Date,
): NotificationInboxRowView {
  const isNew = isNotificationNew(summary, baseline, now);
  const badges = summary.alertCategories.map((category) => ({
    label: ALERT_CATEGORY_TEXTS[category].badgeLabel,
    tone: ALERT_CATEGORY_TEXTS[category].badgeTone,
  }));
  const detail = summary.kind === 'ALERT' ? toAlertDetail(summary) : summary.body;
  const timeLabel = summary.kind === 'ALERT' ? toAlertTimeLabel(summary.createdAt, now) : toAbsoluteDate(summary.createdAt);
  const isPinned = summary.kind === 'NOTICE' && summary.isPinned;
  return {
    id: summary.id,
    kind: summary.kind,
    badges,
    isPinned,
    title: summary.title,
    detail,
    timeLabel,
    isNew,
    accessibilityLabel: [
      isNew ? NEW_ACCESSIBILITY_PREFIX : undefined,
      isPinned ? PINNED_ACCESSIBILITY_TEXT : undefined,
      ...badges.map((badge) => badge.label),
      summary.title,
      detail,
      timeLabel,
    ]
      .filter((part) => part !== undefined && part.length > 0)
      .join(', '),
  };
}

/**
 * 표시 내용 > 구역: 긴급 알림은 기기 달력 날짜별 묶음(오늘, 어제, 날짜), 공지사항은 서버 순서(고정 먼저) 한 구역
 * summaries 와 rowViews 는 같은 순서 (묶음 기준은 작성 시각)
 */
export function toNotificationInboxSections(
  kind: InboxNotificationKind,
  summaries: AppNotificationSummary[],
  rowViews: NotificationInboxRowView[],
  now: Date,
): NotificationInboxSection[] {
  if (rowViews.length === 0) return [];
  if (kind === 'NOTICE') return [{ key: 'notices', data: rowViews }];
  const sections: NotificationInboxSection[] = [];
  summaries.forEach((summary, index) => {
    const key = toLocalDayKey(summary.createdAt);
    const lastSection = sections[sections.length - 1];
    if (lastSection?.key === key) lastSection.data.push(rowViews[index]);
    else sections.push({ key, title: toDayGroupLabel(summary.createdAt, now), data: [rowViews[index]] });
  });
  return sections;
}

// 대표 기사 제목 (+ 외 N건), 기사 제목이 없으면 푸시 본문
function toAlertDetail(summary: AppNotificationSummary): string {
  if (summary.leadArticleTitle === undefined) return summary.body;
  return summary.articleCount > 1 ? `${summary.leadArticleTitle} 외 ${summary.articleCount - 1}건` : summary.leadArticleTitle;
}

// 같은 날은 상대 표기, 그 전은 시각 (날짜는 묶음 이름에 있음)
function toAlertTimeLabel(createdAt: Date, now: Date): string {
  return toLocalDayKey(createdAt) === toLocalDayKey(now) ? toRelativeTime(createdAt, now) : toClockTime(createdAt);
}

/** 알림함 빈 상태의 알림 켜기 안내 (긴급 알림 칸에서 알림 종류가 모두 꺼진 기기만: 알림을 쓸 수 없는 기기·공지사항 칸은 없음) */
export interface NotificationInboxEnableAlertPrompt {
  description: string;
  actionLabel: string;
}

export function toNotificationInboxEnableAlertPrompt(
  kind: InboxNotificationKind,
  hasEnabledAlertCategory: boolean,
  isPushAvailable: boolean,
): NotificationInboxEnableAlertPrompt | undefined {
  if (kind !== 'ALERT' || hasEnabledAlertCategory || !isPushAvailable) return undefined;
  return { description: '알림을 켜면 긴급 보안 소식을 바로 알려 드려요.', actionLabel: '알림 켜러 가기' };
}
