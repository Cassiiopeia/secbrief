import { AppFailure, NETWORK_ERROR_CODE, RESOURCE_NOT_FOUND_ERROR_CODE, shouldRetryQuery } from '@/core/error/app-failure';

describe('조회 재시도 규칙', () => {
  test('찾지 못함은 다시 부르지 않음 (없는 기사·알림을 바로 안내)', () => {
    expect(shouldRetryQuery(0, new AppFailure(RESOURCE_NOT_FOUND_ERROR_CODE, '요청한 내용을 찾을 수 없어요.'))).toBe(false);
  });
  test('연결 끊김 같은 그 밖의 실패는 3회까지 다시 부름', () => {
    const networkFailure = new AppFailure(NETWORK_ERROR_CODE, '인터넷 연결을 확인해 주세요.');
    expect(shouldRetryQuery(0, networkFailure)).toBe(true);
    expect(shouldRetryQuery(2, networkFailure)).toBe(true);
    expect(shouldRetryQuery(3, networkFailure)).toBe(false);
  });
  test('AppFailure 가 아닌 예외도 3회까지 다시 부름', () => {
    expect(shouldRetryQuery(1, new Error('unknown'))).toBe(true);
  });
});
