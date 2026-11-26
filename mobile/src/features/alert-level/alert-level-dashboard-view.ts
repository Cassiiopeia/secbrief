import { toAbsoluteDate, toKoreanMonthDay, toRelativeTime } from '@/core/format/briefing-date-formatter';
import { CISA_KEV_SOURCE_CODE, KISA_SOURCE_CODES } from '@/data/models/article-source-code';
import {
  type AlertLevelChange,
  type AlertLevelDashboard,
  type AlertLevelStatus,
  CYBER_CRISIS_LEVELS,
  type CyberCrisisLevel,
  type InternationalIndicator,
  type WeeklyThreatCounts,
} from '@/data/models/alert-level-dashboard';
import type { SbAlertLevelTone, SbLevelGaugeSegment } from '@/design';

import { URGENT_BROWSE_KEY, toSourceBrowseKey } from '../browse/browse-target';
import {
  ALERT_LEVEL_UNKNOWN_TEXT,
  CYBER_CRISIS_LEVEL_TEXTS,
  INTERNATIONAL_INDICATOR_TEXTS,
  INTERNATIONAL_UNAVAILABLE_TEXT,
} from './alert-level-texts';

/** 머리: 지금 단계 이름(못 읽었으면 없음, 대신 공지 제목) + 시작일 문장 + 한 줄 뜻 + 5단계 막대 */
export interface AlertLevelHeroView {
  levelName?: string;
  tone?: SbAlertLevelTone;
  /** 단계를 못 읽었을 때 그대로 보여 줄 공지 제목 */
  rawTitle?: string;
  sinceLabel: string;
  meaning: string;
  gaugeSegments: SbLevelGaugeSegment[];
  currentStep: number;
  gaugeAccessibilityLabel: string;
  sourceUrl: string;
}

/** 이번 주 숫자 타일: 누르면 그 목록 (모아 보기 묶음 키) */
export interface WeeklyThreatTileView {
  key: string;
  label: string;
  valueText: string;
  detail: string;
  tone: 'feature' | 'neutral';
  browseKey: string;
}

/** 변경 이력 1줄: 날짜 + 바뀐 내용(못 읽었으면 공지 제목) + 원문 */
export interface AlertLevelChangeItemView {
  key: string;
  dateLabel: string;
  title: string;
  metaLabel: string;
  originalUrl: string;
  accessibilityLabel: string;
}

/** 해외 지표 1개: 이름 + 단계 이름 + 지표 자체의 단계 막대 + 뜻 + 확인 시각·출처 */
export interface InternationalIndicatorView {
  key: string;
  name: string;
  levelLabel?: string;
  meaning: string;
  gaugeSegments: SbLevelGaugeSegment[];
  currentStep: number;
  captionLabel: string;
  sourceUrl: string;
  sourceLinkLabel: string;
  accessibilityLabel: string;
}

export interface AlertLevelDashboardView {
  /** 경보단계 글이 하나도 없으면 없음 */
  hero?: AlertLevelHeroView;
  weeklyTiles: WeeklyThreatTileView[];
  changes: AlertLevelChangeItemView[];
  indicators: InternationalIndicatorView[];
}

const CHANGE_META_LABEL = 'KISA 보호나라 원문';
const DIRECTION_WORDS = { raised: '상향', lowered: '하향', kept: '유지' } as const;
const ISSUED_WORD = '발령';

/** 경보단계 화면 데이터 > 표시 내용: 머리 > 이번 주 숫자 > 변경 이력 > 해외 지표 */
export function toAlertLevelDashboardView(dashboard: AlertLevelDashboard, now: Date): AlertLevelDashboardView {
  return {
    hero: dashboard.current === undefined ? undefined : toHeroView(dashboard.current, dashboard.sourceUrl),
    weeklyTiles: dashboard.weeklyCounts === undefined ? [] : toWeeklyTiles(dashboard.weeklyCounts),
    changes: dashboard.changes.map(toChangeItemView),
    indicators: dashboard.internationalIndicators.map((indicator) => toIndicatorView(indicator, now)),
  };
}

/** 5단계 막대 칸 (정상~심각, 이름 표시) */
export const CYBER_CRISIS_GAUGE_SEGMENTS: SbLevelGaugeSegment[] = CYBER_CRISIS_LEVELS.map((level) => ({
  tone: CYBER_CRISIS_LEVEL_TEXTS[level].tone,
  label: CYBER_CRISIS_LEVEL_TEXTS[level].name,
}));

/** 단계 > 1부터 세는 위치 (모르면 0) */
export const toCyberCrisisStep = (level: CyberCrisisLevel | undefined): number =>
  level === undefined ? 0 : CYBER_CRISIS_LEVELS.indexOf(level) + 1;

// 단계를 읽었으면 '10월 22일부터 주의 단계예요', 못 읽었으면 공지 제목 + 원문 확인 안내
function toHeroView(current: AlertLevelStatus, boardUrl: string | undefined): AlertLevelHeroView {
  const sinceDate = toKoreanMonthDay(current.since);
  const sourceUrl = boardUrl ?? current.originalUrl;
  const currentStep = toCyberCrisisStep(current.level);
  if (current.level === undefined) {
    return {
      rawTitle: current.title,
      sinceLabel: `${sinceDate} 공지`,
      meaning: ALERT_LEVEL_UNKNOWN_TEXT,
      gaugeSegments: CYBER_CRISIS_GAUGE_SEGMENTS,
      currentStep,
      gaugeAccessibilityLabel: '사이버위기 경보 단계를 읽지 못했어요',
      sourceUrl,
    };
  }
  const levelText = CYBER_CRISIS_LEVEL_TEXTS[current.level];
  return {
    levelName: levelText.name,
    tone: levelText.tone,
    sinceLabel: `${sinceDate}부터 ${levelText.name} 단계예요`,
    meaning: levelText.meaning,
    gaugeSegments: CYBER_CRISIS_GAUGE_SEGMENTS,
    currentStep,
    gaugeAccessibilityLabel: `사이버위기 경보 5단계 중 ${currentStep}단계, ${levelText.name}`,
    sourceUrl,
  };
}

function toWeeklyTiles(counts: WeeklyThreatCounts): WeeklyThreatTileView[] {
  return [
    {
      key: 'exploited',
      label: '악용 중 · KEV',
      valueText: `+${counts.exploitedVulnerabilityCount}`,
      detail: '전 세계 새 등재',
      tone: 'feature',
      browseKey: toSourceBrowseKey(CISA_KEV_SOURCE_CODE),
    },
    { key: 'urgent', label: '긴급 소식', valueText: String(counts.urgentArticleCount), detail: '이번 주', tone: 'neutral', browseKey: URGENT_BROWSE_KEY },
    {
      key: 'kisaNotice',
      label: 'KISA 보안공지',
      valueText: String(counts.kisaNoticeCount),
      detail: '이번 주 새 글',
      tone: 'neutral',
      browseKey: toSourceBrowseKey(KISA_SOURCE_CODES.notice),
    },
  ];
}

// 바뀐 내용: '관심 → 주의 상향', 바뀌기 전 단계를 모르면 '주의 발령', 단계를 못 읽었으면 공지 제목 그대로
function toChangeItemView(change: AlertLevelChange): AlertLevelChangeItemView {
  const dateLabel = toAbsoluteDate(change.publishedAt);
  const title = toChangeTitle(change);
  return {
    key: change.articleId,
    dateLabel,
    title,
    metaLabel: CHANGE_META_LABEL,
    originalUrl: change.originalUrl,
    accessibilityLabel: `${dateLabel}, ${title}, 원문 보기`,
  };
}

function toChangeTitle(change: AlertLevelChange): string {
  if (change.toLevel === undefined) return change.title;
  const toName = CYBER_CRISIS_LEVEL_TEXTS[change.toLevel].name;
  if (change.fromLevel === undefined || change.direction === undefined) return `${toName} ${ISSUED_WORD}`;
  return `${CYBER_CRISIS_LEVEL_TEXTS[change.fromLevel].name} → ${toName} ${DIRECTION_WORDS[change.direction]}`;
}

// 지표 자체 단계 수만큼의 막대 (CIS 5칸, SANS 4칸), 못 읽었으면 전부 흐리게 + 확인하지 못했다는 문구
function toIndicatorView(indicator: InternationalIndicator, now: Date): InternationalIndicatorView {
  const indicatorText = INTERNATIONAL_INDICATOR_TEXTS[indicator.code];
  const levelTexts = Object.values(indicatorText.levels);
  const levelText = indicator.level === undefined ? undefined : indicatorText.levels[indicator.level];
  const stepLabel = levelText === undefined ? undefined : `${indicator.levelCount}단계 중 ${indicator.levelStep}단계`;
  const checkedLabel = indicator.checkedAt === undefined ? undefined : `${toRelativeTime(indicator.checkedAt, now)} 확인`;
  return {
    key: indicator.code,
    name: indicatorText.name,
    levelLabel: levelText?.label,
    meaning: levelText?.meaning ?? INTERNATIONAL_UNAVAILABLE_TEXT,
    gaugeSegments: levelTexts.map((text) => ({ tone: text.tone })),
    currentStep: levelText === undefined ? 0 : indicator.levelStep,
    captionLabel: [stepLabel, indicatorText.scope, checkedLabel].filter((part) => part !== undefined).join(' · '),
    sourceUrl: indicator.sourceUrl,
    sourceLinkLabel: indicatorText.sourceLinkLabel,
    accessibilityLabel: [indicatorText.name, levelText?.label ?? '확인하지 못했어요', stepLabel, levelText?.meaning]
      .filter((part) => part !== undefined)
      .join(', '),
  };
}
