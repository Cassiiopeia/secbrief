import type { BriefingItem } from '@/data/models/briefing-item';
import type { SbBadgeContent, SbCvssGaugeValue, SbSeverityTone } from '@/design';

import { toKindLabel, toTopicLabel } from './article-labels';

const URGENT_BADGE_LABEL = '긴급';
// CISA KEV 등재 = 실제 공격에 쓰이는 취약점 (대응 모아보기 행도 같은 말)
export const EXPLOITED_BADGE_LABEL = '악용 중';
// CVSS 심각도 구간 하한 (SbColors 심각도 4단계와 같은 기준), 만점
const CVSS_CRITICAL_MINIMUM_SCORE = 9;
const CVSS_HIGH_MINIMUM_SCORE = 7;
const CVSS_MEDIUM_MINIMUM_SCORE = 4;
const CVSS_MAXIMUM_SCORE = 10;
const CVSS_CAPTION = 'CVSS';
// 목록 한 건에 보일 배지 수 한도
const FEED_BADGE_MAXIMUM_COUNT = 2;

/** CVSS 점수 > 심각도 색조: 9.0 이상 critical, 7.0 이상 high, 4.0 이상 medium, 그 미만 low */
export function toCvssSeverityTone(cvssScore: number): SbSeverityTone {
  if (cvssScore >= CVSS_CRITICAL_MINIMUM_SCORE) return 'critical';
  if (cvssScore >= CVSS_HIGH_MINIMUM_SCORE) return 'high';
  if (cvssScore >= CVSS_MEDIUM_MINIMUM_SCORE) return 'medium';
  return 'low';
}

/** 소식 > CVSS 게이지 값: 점수(원문 값 그대로) + 버전 표기 + 10점 대비 비율 + 심각도 색조. 점수 없으면 없음 */
export function toCvssGaugeValue(item: BriefingItem): SbCvssGaugeValue | undefined {
  if (item.cvssScore === undefined) return undefined;
  return {
    scoreLabel: item.cvssScore.toFixed(1),
    caption: item.cvssVersion ? `${CVSS_CAPTION} ${item.cvssVersion}` : CVSS_CAPTION,
    ratio: Math.min(Math.max(item.cvssScore / CVSS_MAXIMUM_SCORE, 0), 1),
    tone: toCvssSeverityTone(item.cvssScore),
  };
}

/**
 * 소식 > 배지 전체 (강한 것부터): 긴급 > 악용 중(KEV) > 회사·제품 > 주제(일반 제외) > 종류(주제와 이름이 같으면 생략)
 * CVSS 는 게이지로, AI 번역은 번역 아이콘으로 따로 보여 줌
 */
export function toArticleBadges(item: BriefingItem): SbBadgeContent[] {
  const badges: SbBadgeContent[] = [];
  const topicLabel = item.topic === 'general' ? undefined : toTopicLabel(item.topic);
  const kindLabel = toKindLabel(item.kind);
  if (item.isUrgent) badges.push({ label: URGENT_BADGE_LABEL, tone: 'urgent' });
  if (item.isKev) badges.push({ label: EXPLOITED_BADGE_LABEL, tone: 'exploited' });
  if (item.subjectName !== undefined) badges.push({ label: item.subjectName, tone: 'subject' });
  if (topicLabel !== undefined) badges.push({ label: topicLabel, tone: 'neutral' });
  if (kindLabel !== topicLabel) badges.push({ label: kindLabel, tone: 'neutral' });
  return badges;
}

/** 배지 > 자리만 봐도 아는 분류 이름을 뺀 배지 (예: 취약점 탭의 '취약점', 상세 머리의 종류) */
export function toBadgesWithoutImplied(badges: SbBadgeContent[], impliedLabel: string | undefined): SbBadgeContent[] {
  return badges.filter((badge) => badge.tone !== 'neutral' || badge.label !== impliedLabel);
}

/**
 * 배지 전체 > 목록 한 건에 보일 배지 (2개까지, 강한 것부터)
 * 빼는 것 2개: 자리만 봐도 아는 분류, 조밀한 행의 분류 배지
 */
export function toFeedBadges(badges: SbBadgeContent[], isCompact: boolean, impliedLabel?: string): SbBadgeContent[] {
  return toBadgesWithoutImplied(badges, impliedLabel)
    .filter((badge) => !isCompact || badge.tone !== 'neutral')
    .slice(0, FEED_BADGE_MAXIMUM_COUNT);
}
