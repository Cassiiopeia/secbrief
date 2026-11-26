/** 크기 토큰 (단위: 논리 픽셀) */
export const sbSize = {
  /** 누를 수 있는 것의 최소 높이·너비 (접근성 기준 44) */
  minimumTouchTarget: 44,
  /** 소식 목록 행 썸네일 */
  articleThumbnail: 72,
  /** 조밀한 행 썸네일 */
  compactThumbnail: 56,
  /** 큰 카드: 가로 레일 안 카드 너비, 그림 높이(레일 / 화면 너비 카드) */
  railCardWidth: 264,
  railCoverHeight: 124,
  wideCoverHeight: 172,
  /** CVSS 게이지 너비 */
  cvssGauge: 60,
  /** 주제 바로가기 그림 */
  shortcutArt: 56,
  /** 오늘 탭 스트림 썸네일 */
  streamThumbnail: 68,
  /** 빈 상태·오류 그림 (정사각, 원본은 3배 크기 WebP) */
  stateIllustration: 136,
  /** 행·입력창 안 아이콘 (돋보기, 지우기, 오른쪽 화살표) */
  rowIcon: 20,
  /** 안 본 글 점 (알림 종, 알림함 칸·줄) */
  unreadDot: 8,
} as const;

/** 비율(0~1) > 너비 % 로 바꿀 때 곱하는 값 (비율 막대) */
export const sbFullWidthPercent = 100;
