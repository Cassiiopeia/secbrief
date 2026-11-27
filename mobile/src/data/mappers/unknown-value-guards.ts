/**
 * 서버 응답·저장값 같은 unknown 값 모양 판정·글 정리 (매퍼·API 클라이언트 공용)
 */

/** 객체인지 (null 아님, 배열도 객체로 봄) */
export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** 배열이 아닌 객체인지 */
export function isNonArrayObject(value: unknown): value is Record<string, unknown> {
  return isObject(value) && !Array.isArray(value);
}

/** 받은 값 > 앞뒤 빈칸을 뺀 글 (글이 아니거나 빈 글·공백만 있는 글이면 없음) */
export function toNonBlankText(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmedText = value.trim();
  return trimmedText === '' ? undefined : trimmedText;
}
