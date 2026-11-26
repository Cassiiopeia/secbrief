/**
 * 앱 전체 색 토큰 (E 스트림 테마). 화면은 hex 값이 아니라 이 의미 이름으로만 색을 고른다
 * 원본 brand/brand-colors.json 과 함께 바꾼다 (값 일치는 tests/architecture 가 검사)
 */
export interface SbColors {
  /** 바탕 / 표면 2단 (카드, 한 단 올라온 타일) */
  background: string;
  surface: string;
  surfaceRaised: string;
  /** 글자 3단 (본문, 보조, 흐린 라벨) */
  label: string;
  secondaryLabel: string;
  tertiaryLabel: string;
  /** 읽은 소식 제목 (본문보다 흐리되 다크에서도 읽히는 밝기) */
  readLabel: string;
  separator: string;
  /** 로딩 뼈대 블록 (바탕과 구분되는 밝기) */
  skeleton: string;
  /** 민트 강조 (긴급 타일, 채움 버튼) 와 그 위 글자 */
  accent: string;
  onAccent: string;
  /** 보라 타일 (동향 1위) 과 그 위 글자 */
  feature: string;
  onFeature: string;
  /** 긴급 표시 (레일 점, 속보 라벨). 바탕과 대비되는 쪽을 테마별로 고름 */
  urgent: string;
  /** 알림 막대(본문색 알약) 위 확인 아이콘: 먹색 알약엔 민트, 흰 알약엔 보라 */
  toastIcon: string;
  /** 취약점 심각도 4단계 (CVSS: 9.0 이상, 7.0 이상, 4.0 이상, 그 미만) */
  severityCritical: string;
  severityHigh: string;
  severityMedium: string;
  severityLow: string;
}

export const sbLightColors: SbColors = {
  background: '#F4F4F0',
  surface: '#FFFFFF',
  surfaceRaised: '#E9E9E4',
  label: '#131313',
  secondaryLabel: '#2D2D2D',
  tertiaryLabel: '#6B6B6B',
  readLabel: '#6B6B6B',
  separator: '#D6D6D0',
  skeleton: '#DFDFD9',
  accent: '#3CFFD0',
  onAccent: '#131313',
  feature: '#5200FF',
  onFeature: '#FFFFFF',
  urgent: '#5200FF',
  toastIcon: '#3CFFD0',
  severityCritical: '#D92D20',
  severityHigh: '#C2570C',
  severityMedium: '#9A7500',
  severityLow: '#51627D',
};

export const sbDarkColors: SbColors = {
  background: '#131313',
  surface: '#1D1D1D',
  surfaceRaised: '#2D2D2D',
  label: '#FFFFFF',
  secondaryLabel: '#E9E9E9',
  tertiaryLabel: '#949494',
  readLabel: '#B4B4B4',
  separator: '#2D2D2D',
  skeleton: '#3A3A3A',
  accent: '#3CFFD0',
  onAccent: '#131313',
  feature: '#5200FF',
  onFeature: '#FFFFFF',
  urgent: '#3CFFD0',
  toastIcon: '#5200FF',
  severityCritical: '#FF6B5E',
  severityHigh: '#FFA24A',
  severityMedium: '#F2CF4A',
  severityLow: '#8FA3C2',
};

/** 심각도 색조 4단계 (CVSS 구간) */
export type SbSeverityTone = 'critical' | 'high' | 'medium' | 'low';

/** 심각도 색조 > 색 토큰 값 */
export function toSbSeverityColor(colors: SbColors, tone: SbSeverityTone): string {
  switch (tone) {
    case 'critical':
      return colors.severityCritical;
    case 'high':
      return colors.severityHigh;
    case 'medium':
      return colors.severityMedium;
    case 'low':
      return colors.severityLow;
  }
}

/** 소식 제목 글자색: 읽은 소식은 흐리게 */
export function toSbArticleTitleColor(colors: SbColors, isRead: boolean): string {
  return isRead ? colors.readLabel : colors.label;
}
