import {
  AppFailure,
  NETWORK_ERROR_CODE,
  QUERY_RETRY_DELAY_MILLISECONDS,
  RESOURCE_NOT_FOUND_ERROR_CODE,
  shouldRetryQuery,
  TIMEOUT_ERROR_CODE,
} from '@/core/error/app-failure';

describe('조회 재시도 규칙', () => {
  test('찾지 못함은 다시 부르지 않음 (없는 기사·알림을 바로 안내)', () => {
    expect(shouldRetryQuery(0, new AppFailure(RESOURCE_NOT_FOUND_ERROR_CODE, '요청한 내용을 찾을 수 없어요.'))).toBe(false);
  });
  test('연결 끊김 같은 그 밖의 실패는 1번만 다시 부름 (서버 장애 때 오류 화면까지 약 1~2초, #87)', () => {
    const networkFailure = new AppFailure(NETWORK_ERROR_CODE, '인터넷 연결을 확인해 주세요.');
    expect(shouldRetryQuery(0, networkFailure)).toBe(true);
    expect(shouldRetryQuery(1, networkFailure)).toBe(false);
    expect(shouldRetryQuery(0, new AppFailure('HTTP_502', '서버에 문제가 생겼어요.'))).toBe(true);
    expect(QUERY_RETRY_DELAY_MILLISECONDS).toBeLessThanOrEqual(2_000);
  });
  test('시간 초과는 다시 부르지 않음 (이미 요청 제한 시간만큼 기다림)', () => {
    expect(shouldRetryQuery(0, new AppFailure(TIMEOUT_ERROR_CODE, '응답이 늦어요. 잠시 후 다시 시도해 주세요.'))).toBe(false);
  });
  test('AppFailure 가 아닌 예외도 1번만 다시 부름', () => {
    expect(shouldRetryQuery(0, new Error('unknown'))).toBe(true);
    expect(shouldRetryQuery(1, new Error('unknown'))).toBe(false);
  });
});
