/** 간격 토큰 (단위: 논리 픽셀). 시안 값 그대로 */
export const sbSpacing = {
  extraSmall: 4,
  small: 8,
  compact: 12,
  medium: 16,
  large: 24,
  extraLarge: 32,
  /** 타일 바깥 좌우 여백 (타일은 화면 가장자리에 더 붙음) */
  tileHorizontal: 12,
  /** 글·목록의 좌우 여백 */
  pageHorizontal: 16,
  /** 타일 사이 간격 */
  tileGap: 8,
  /** 타일 안쪽 여백 (긴급 타일 18, 통계 타일 14) */
  tilePadding: 18,
  statTilePadding: 14,
  /** 구역(스트림 등) 위 여백 */
  sectionTop: 22,
  /** 스트림 레일: 선에서 글까지 들여쓰기 / 항목 사이 */
  railIndent: 14,
  railItemGap: 16,
} as const;
