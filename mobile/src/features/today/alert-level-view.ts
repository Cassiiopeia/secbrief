import { toDottedMonthDay } from '@/core/format/briefing-date-formatter';
import type { AlertLevelDashboard } from '@/data/models/alert-level-dashboard';
import type { SbAlertLevelTone } from '@/design';

import { ALERT_LEVEL_SCREEN_TITLE, CYBER_CRISIS_LEVEL_TEXTS } from '../alert-level/alert-level-texts';

/** 오늘 탭 위쪽 사이버위기 경보 띠 (누르면 경보단계 화면) */
export interface AlertLevelView {
  capsLabel: string;
  /** 예: 주의 · 10.22부터 (단계를 못 읽었으면 공지 제목 그대로) */
  title: string;
  /** 단계 색 (못 읽었으면 없음: 띠 기본 색) */
  tone?: SbAlertLevelTone;
  accessibilityLabel: string;
}

/**
 * 경보단계 화면 데이터 > 띠: 지금 단계 + 시작일 (경보단계 글이 없으면 띠 없음)
 */
export function toAlertLevelView(dashboard: AlertLevelDashboard): AlertLevelView | undefined {
  const { current } = dashboard;
  if (current === undefined) return undefined;
  const levelText = current.level === undefined ? undefined : CYBER_CRISIS_LEVEL_TEXTS[current.level];
  const title = levelText === undefined ? current.title : `${levelText.name} · ${toDottedMonthDay(current.since)}부터`;
  return {
    capsLabel: ALERT_LEVEL_SCREEN_TITLE,
    title,
    tone: levelText?.tone,
    accessibilityLabel: `${ALERT_LEVEL_SCREEN_TITLE}, ${title}, 자세히 보기`,
  };
}
