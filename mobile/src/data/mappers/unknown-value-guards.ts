/**
 * 서버 응답·저장값 같은 unknown 값 모양 판정 (매퍼·API 클라이언트 공용)
 */

/** 객체인지 (null 아님, 배열도 객체로 봄) */
export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** 배열이 아닌 객체인지 */
export function isNonArrayObject(value: unknown): value is Record<string, unknown> {
  return isObject(value) && !Array.isArray(value);
}
