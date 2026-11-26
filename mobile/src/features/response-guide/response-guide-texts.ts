import type { ResponseGuideMatchReason, ResponseGuideSectionKind } from '@/data/models/response-guide';
import type { SbIconName } from '@/design';

/**
 * 대응 모아보기 화면 문구 단일 원본 (기사 상세 구역, 알림 상세 진입)
 * 구역 3개: title 카드 제목, caption 무엇을 모았는지, iconName 카드 아이콘, isEmphasized 강조 테두리(공식 대응만)
 */
export const RESPONSE_GUIDE_SECTION_TEXTS: Record<
  ResponseGuideSectionKind,
  { title: string; caption: string; iconName: SbIconName; isEmphasized: boolean }
> = {
  OFFICIAL: {
    title: '공식 대응',
    caption: 'KISA와 제조사가 낸 보안 공지예요',
    iconName: 'verified-user',
    isEmphasized: true,
  },
  // 기사 상세 위쪽의 '확인된 정보'(이 글의 원문 값)와 겹치지 않게 '취약점'을 붙임
  FACT: {
    title: '확인된 취약점 정보',
    caption: 'NVD·CISA KEV·GitHub에 등록된 항목이에요',
    iconName: 'fact-check',
    isEmphasized: false,
  },
  NEWS: {
    title: '관련 기사',
    caption: '같은 내용을 다룬 보도예요',
    iconName: 'article',
    isEmphasized: false,
  },
};

/** 묶인 이유 > 행에 붙는 짧은 표시 (같은 CVE 는 겹친 번호를 함께 보여 줌) */
export const RESPONSE_GUIDE_MATCH_LABELS: Record<ResponseGuideMatchReason, string> = {
  SAME_CVE: '같은 CVE',
  SAME_PRODUCT: '같은 제품',
  SAME_VENDOR: '같은 제조사',
};

/** 대응 모아보기 전체 머리 (기사 상세) */
export const RESPONSE_GUIDE_HEADER_TEXTS = {
  title: '대응 모아보기',
  caption: '같은 취약점을 다룬 공개 자료를 모았어요',
} as const;

/** KEV 조치 기한 표시 앞말 (값은 서버가 준 원문 그대로) */
export const ACTION_DUE_DATE_PREFIX = '조치 기한';
