import type { TextStyle } from 'react-native';

/**
 * 글꼴 이름: 굵기마다 파일이 달라 이름도 따로 둔다 (Android 는 fontWeight 로 굵기를 못 고름)
 * src/app/_layout.tsx 의 글꼴 불러오기와 이름이 같아야 함
 */
export const sbFontFamily = {
  sansRegular: 'Pretendard-Regular',
  sansMedium: 'Pretendard-Medium',
  sansSemiBold: 'Pretendard-SemiBold',
  sansBold: 'Pretendard-Bold',
  sansExtraBold: 'Pretendard-ExtraBold',
  monoRegular: 'D2Coding-Regular',
  monoBold: 'D2Coding-Bold',
} as const;

/**
 * 글자 토큰 (E 스트림 테마). 색은 넣지 않고 쓰는 곳에서 SbColors 로 입힌다
 * 자간은 시안의 em 값 x 글자 크기, 줄높이는 시안 배수 x 글자 크기 (단위: 논리 픽셀)
 */
export const sbTypography = {
  /** 마스트헤드 (SEC / BRIEF): 34 / 800 / -0.05em / 줄높이 0.9 */
  masthead: { fontFamily: sbFontFamily.sansExtraBold, fontSize: 34, lineHeight: 31, letterSpacing: -1.7 },
  /** 긴급 타일 제목: 23 / 800 / -0.035em */
  tileTitle: { fontFamily: sbFontFamily.sansExtraBold, fontSize: 23, lineHeight: 28, letterSpacing: -0.8 },
  /** 통계 타일의 큰 값 (랜섬웨어, 12일): 26 / 800 / -0.04em. 한글 받침이 잘리지 않게 줄높이 30 */
  statValue: { fontFamily: sbFontFamily.sansExtraBold, fontSize: 26, lineHeight: 30, letterSpacing: -1.04 },
  /** 스트림 항목 제목: 16 / 700 / -0.025em */
  streamTitle: { fontFamily: sbFontFamily.sansBold, fontSize: 16, lineHeight: 21, letterSpacing: -0.4 },
  /** 빈 상태·오류 화면 제목 */
  headline: { fontFamily: sbFontFamily.sansBold, fontSize: 18, lineHeight: 24, letterSpacing: -0.45 },
  /** 구역 제목 (국내 공지, 알림): 15 / 700 */
  sectionTitle: { fontFamily: sbFontFamily.sansBold, fontSize: 15, lineHeight: 20, letterSpacing: -0.3 },
  /** 설정·목록 행 제목: 15 / 600 */
  rowTitle: { fontFamily: sbFontFamily.sansSemiBold, fontSize: 15, lineHeight: 21, letterSpacing: -0.2 },
  /** CVSS 게이지 점수 (고정폭 굵게) */
  gaugeValue: { fontFamily: sbFontFamily.monoBold, fontSize: 20, lineHeight: 24 },
  /** 기사 상세의 긴 글 (요약·핵심): 15 / 400, 줄 간격 넉넉히 */
  reading: { fontFamily: sbFontFamily.sansRegular, fontSize: 15, lineHeight: 24 },
  /** 확인된 정보 값 (버전·날짜 등 원문 값): 고정폭 13 */
  factValue: { fontFamily: sbFontFamily.monoRegular, fontSize: 13, lineHeight: 19 },
  /** 기사 상세 제목: 22 / 800 */
  detailTitle: { fontFamily: sbFontFamily.sansExtraBold, fontSize: 22, lineHeight: 29, letterSpacing: -0.6 },
  body: { fontFamily: sbFontFamily.sansRegular, fontSize: 14, lineHeight: 21 },
  /** 타일 안 보조 설명: 12 / 500 */
  caption: { fontFamily: sbFontFamily.sansMedium, fontSize: 12, lineHeight: 17 },
  button: { fontFamily: sbFontFamily.sansBold, fontSize: 14, lineHeight: 17 },
  /** 대문자 고정폭 라벨 (긴급 / CVSS 9.8): 10.5 / 700 / 0.12em */
  capsLabel: { fontFamily: sbFontFamily.monoBold, fontSize: 10.5, lineHeight: 16, letterSpacing: 1.26 },
  /** 스트림 시간 라벨 (09:12 · KISA): 10.5 / 400 / 0.08em */
  timeLabel: { fontFamily: sbFontFamily.monoRegular, fontSize: 10.5, lineHeight: 16, letterSpacing: 0.84 },
  /** 하단 탭 글자 (선택 시 굵은 쪽) */
  tabLabel: { fontFamily: sbFontFamily.sansMedium, fontSize: 10.5, lineHeight: 14 },
  tabLabelSelected: { fontFamily: sbFontFamily.sansBold, fontSize: 10.5, lineHeight: 14 },
} as const satisfies Record<string, TextStyle>;

export type SbTextVariant = keyof typeof sbTypography;

/**
 * 기기 글자 크기 확대 한도 (배율): 본문은 넉넉히, 자리가 고정된 글자(머리·탭·고정폭 라벨)는 조금만
 */
export const sbMaximumFontScale = { text: 1.6, compact: 1.25 } as const;

// 자리가 고정돼 크게 키우면 깨지는 글자 토큰
const COMPACT_SCALE_VARIANTS: SbTextVariant[] = ['masthead', 'capsLabel', 'timeLabel', 'tabLabel', 'tabLabelSelected', 'statValue', 'gaugeValue'];

/** 글자 토큰 > 확대 한도 */
export function toSbMaximumFontScale(variant: SbTextVariant): number {
  return COMPACT_SCALE_VARIANTS.includes(variant) ? sbMaximumFontScale.compact : sbMaximumFontScale.text;
}
