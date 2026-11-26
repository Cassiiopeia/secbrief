/** 줄 수 제한 토큰 (넘치면 말줄임) */
export const sbMaximumLines = {
  /** 한 줄 글 (게시자·날짜, 원문 제목, 배지, 버튼, 칩) */
  singleLine: 1,
  /** 조밀한 행·타임라인·상태 띠 제목 */
  compactTitle: 2,
  /** 카드·스트림 제목 */
  cardTitle: 3,
} as const;
