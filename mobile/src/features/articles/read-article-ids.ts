// 기기에 남기는 읽은 기사 수 한도 (넘으면 오래된 것부터 버림)
export const READ_ARTICLE_MAXIMUM_COUNT = 500;

/** 읽은 기사 목록에 한 건 더하기: 이미 있으면 맨 뒤(가장 최근)로 옮기고, 한도를 넘으면 오래된 것부터 버림 */
export function toReadArticleIdsWith(readArticleIds: string[], articleId: string): string[] {
  const nextIds = [...readArticleIds.filter((id) => id !== articleId), articleId];
  return nextIds.slice(Math.max(nextIds.length - READ_ARTICLE_MAXIMUM_COUNT, 0));
}
