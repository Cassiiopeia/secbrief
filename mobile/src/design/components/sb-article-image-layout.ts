/** 소식 그림 배치 규칙 (순수 함수): 보일 이미지 주소, 제목 줄 수 */

/** 대표 이미지 주소 <-> 실패한 주소 > 보일 주소 (없음·실패면 없음) */
export function toSbShownArticleImageUrl(imageUrl: string | undefined, failedImageUrl: string | undefined): string | undefined {
  return imageUrl !== undefined && imageUrl !== failedImageUrl ? imageUrl : undefined;
}

/** 소식 제목 줄 수: 이미지가 있으면 배치의 제한 줄 수, 없으면 제한 없음 (제목 전체를 글로) */
export function toSbArticleTitleMaximumLines(hasImage: boolean, imageLayoutMaximumLines: number): number | undefined {
  return hasImage ? imageLayoutMaximumLines : undefined;
}
