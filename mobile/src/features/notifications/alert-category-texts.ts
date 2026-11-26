import type { AlertCategory } from '@/data/models/alert-category';
import type { SbBadgeTone } from '@/design';

/**
 * 알림 종류 > 화면 문구 단일 원본 (설정 스위치, 알림함 배지, 알림 상세 설명)
 * title 이름, detail 스위치 설명, badgeLabel·badgeTone 알림함 배지, reason 알림 상세에서 왜 알렸는지
 */
export const ALERT_CATEGORY_TEXTS: Record<
  AlertCategory,
  { title: string; detail: string; badgeLabel: string; badgeTone: SbBadgeTone; reason: string }
> = {
  KISA_URGENT: {
    title: 'KISA 긴급 공지',
    detail: 'KISA 보호나라가 긴급으로 올린 공지',
    badgeLabel: 'KISA 긴급',
    badgeTone: 'urgent',
    reason: 'KISA 보호나라가 긴급으로 알린 공지예요',
  },
  KEV: {
    title: '악용 중인 취약점',
    detail: 'CISA KEV 에 오른 취약점',
    badgeLabel: '악용 중',
    badgeTone: 'exploited',
    reason: '실제 공격에 쓰인 것이 확인된 취약점이에요 (CISA KEV 등재)',
  },
  CRITICAL_CVE: {
    title: '고위험 취약점',
    detail: 'CVSS 9.0 이상',
    badgeLabel: '고위험',
    badgeTone: 'critical',
    reason: 'CVSS 9.0 이상으로 평가된 고위험 취약점이에요',
  },
};
