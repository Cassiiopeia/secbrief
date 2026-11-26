import type { CyberCrisisLevel, InternationalIndicatorCode } from '@/data/models/alert-level-dashboard';
import type { SbAlertLevelTone } from '@/design';

/**
 * 경보단계 화면 문구 단일 원본: 국내 5단계 이름·색조·뜻, 해외 지표 2개의 이름·단계별 뜻
 * 뜻은 각 기관이 공개한 안내를 짧게 옮긴 것 (KISA 보호나라 경보단계 안내 표, cisecurity.org 경보 단계 안내, isc.sans.edu InfoCon 정의)
 */

export interface CyberCrisisLevelText {
  name: string;
  tone: SbAlertLevelTone;
  /** 한 줄 뜻 (KISA 보호나라 경보단계 안내 표) */
  meaning: string;
}

/** 국내 사이버위기 경보 5단계 (낮은 단계부터 CYBER_CRISIS_LEVELS 순서) */
export const CYBER_CRISIS_LEVEL_TEXTS: Record<CyberCrisisLevel, CyberCrisisLevelText> = {
  normal: { name: '정상', tone: 'green', meaning: '인터넷 이용에 지장이 없고 위험도 낮은 위협만 보이는 단계예요.' },
  attention: {
    name: '관심',
    tone: 'blue',
    meaning: '위험한 악성코드·취약점이 나타나거나 해외 공격이 국내로 번질 수 있는 단계예요.',
  },
  caution: {
    name: '주의',
    tone: 'yellow',
    meaning: '일부 정보통신망에 장애가 있거나 침해사고가 여러 기관으로 번질 수 있는 단계예요.',
  },
  alert: { name: '경계', tone: 'orange', meaning: '여러 통신사 망에 장애가 생기거나 침해사고가 크게 번질 수 있는 단계예요.' },
  severe: { name: '심각', tone: 'red', meaning: '국가 차원의 주요 정보통신망이 마비되거나 전국에서 피해가 생긴 단계예요.' },
};

export const ALERT_LEVEL_SCREEN_TITLE = '사이버위기 경보';
export const ALERT_LEVEL_CAPTION = '사이버위기 경보 · KISA';
export const ALERT_LEVEL_SOURCE_LINK_LABEL = 'KISA 보호나라 경보단계 원문 보기';
export const ALERT_LEVEL_UNKNOWN_TEXT = '공지 제목에서 단계를 읽지 못했어요. 원문에서 확인해 주세요.';

export interface InternationalIndicatorLevelText {
  /** 화면에 보이는 단계 이름 (지표 글자 그대로, 첫 글자 대문자) */
  label: string;
  tone: SbAlertLevelTone;
  meaning: string;
}

export interface InternationalIndicatorText {
  name: string;
  /** 무엇을 기준으로 정한 지표인지 */
  scope: string;
  sourceLinkLabel: string;
  /** 지표가 주는 단계 값 > 문구 (낮은 단계부터) */
  levels: Record<string, InternationalIndicatorLevelText>;
}

/** 해외 지표 2개: 하나의 국제 기준이 아니라 각 기관이 따로 정한 지표 */
export const INTERNATIONAL_INDICATOR_TEXTS: Record<InternationalIndicatorCode, InternationalIndicatorText> = {
  cisMsIsac: {
    name: '미국 CIS(MS-ISAC) 사이버 경보',
    scope: '미국 주·지방 정부 기관 대상 위협 기준',
    sourceLinkLabel: 'cisecurity.org',
    levels: {
      low: { label: 'Low', tone: 'green', meaning: '알려진 공격 활동이 없는 평상시예요.' },
      guarded: { label: 'Guarded', tone: 'blue', meaning: '공격 징후가 보여 경계를 높인 단계예요.' },
      elevated: { label: 'Elevated', tone: 'yellow', meaning: '실제 공격 활동이 확인된 단계예요.' },
      high: { label: 'High', tone: 'orange', meaning: '공격이 넓게 퍼지고 있는 단계예요.' },
      severe: { label: 'Severe', tone: 'red', meaning: '대규모 공격으로 위기 상황인 단계예요.' },
    },
  },
  sansIscInfocon: {
    name: 'SANS 인터넷 스톰 센터 InfoCon',
    scope: '인터넷 전체 트래픽 기준',
    sourceLinkLabel: 'isc.sans.edu',
    levels: {
      green: { label: 'Green', tone: 'green', meaning: '특별한 새 위협이 없는 평상시예요.' },
      yellow: { label: 'Yellow', tone: 'yellow', meaning: '새로운 큰 위협을 추적하고 있는 단계예요.' },
      orange: { label: 'Orange', tone: 'orange', meaning: '인터넷 연결에 큰 장애가 임박했거나 진행 중이에요.' },
      red: { label: 'Red', tone: 'red', meaning: '인터넷 넓은 지역에서 연결이 끊긴 상황이에요.' },
    },
  },
};

export const INTERNATIONAL_SECTION_TITLE = '해외 지표';
export const INTERNATIONAL_SECTION_CAPTION = '기관마다 따로 정한 지표예요. 하나의 국제 기준은 아니에요.';
export const INTERNATIONAL_UNAVAILABLE_TEXT = '지금은 확인하지 못했어요. 원문에서 확인해 주세요.';
