/**
 * API 클라이언트: data 꺼내기, 쿼리 조립, 실패 코드 변환 5종 (fetch 가짜, 실제 통신 없음)
 */
import { apiConfig } from '@/core/config/api-config';
import { fetchApiData } from '@/data/sources/secbrief-api-client';

const fetchMock = jest.fn();

beforeEach(() => {
  fetchMock.mockReset();
  global.fetch = fetchMock as unknown as typeof fetch;
});

afterEach(() => {
  jest.useRealTimers();
});

/** JSON 본문 응답 1회 */
function mockFetchOnce(status: number, jsonBody: unknown) {
  mockFetchTextOnce(status, JSON.stringify(jsonBody));
}

/** 글자 본문 응답 1회 */
function mockFetchTextOnce(status: number, text: string) {
  fetchMock.mockResolvedValueOnce({ ok: status >= 200 && status < 300, status, text: async () => text });
}

test('성공 응답의 data 만 돌려준다', async () => {
  mockFetchOnce(200, { data: { value: 1 } });
  await expect(fetchApiData('/api/v1/today')).resolves.toEqual({ value: 1 });
});

test('undefined 쿼리 값은 주소에서 뺀다', async () => {
  mockFetchOnce(200, { data: [] });
  await fetchApiData('/api/v1/articles', { kind: 'NEWS', region: undefined });
  expect(fetchMock.mock.calls[0][0]).toBe(`${apiConfig.baseUrl}/api/v1/articles?kind=NEWS`);
});

test('쿼리 값이 없으면 물음표를 붙이지 않는다', async () => {
  mockFetchOnce(200, { data: [] });
  await fetchApiData('/api/v1/sources');
  expect(fetchMock.mock.calls[0][0]).toBe(`${apiConfig.baseUrl}/api/v1/sources`);
});

test('서버 에러 본문의 errorCode 를 그대로 쓴다', async () => {
  mockFetchOnce(400, { errorCode: 'INVALID_CURSOR', message: '목록 위치 값이 올바르지 않아요.' });
  await expect(fetchApiData('/x')).rejects.toMatchObject({
    errorCode: 'INVALID_CURSOR',
    userMessage: '목록 위치 값이 올바르지 않아요.',
  });
});

test('rejectsNonJsonBodyWithInvalidResponseCode', async () => {
  mockFetchTextOnce(200, '<html>proxy error</html>');
  await expect(fetchApiData('/x')).rejects.toMatchObject({ errorCode: 'APP_INVALID_RESPONSE' });
});

test('data 가 없는 성공 응답은 APP_INVALID_RESPONSE', async () => {
  mockFetchOnce(200, { value: 1 });
  await expect(fetchApiData('/x')).rejects.toMatchObject({ errorCode: 'APP_INVALID_RESPONSE' });
});

test('오류 본문 없는 502 는 HTTP_502', async () => {
  mockFetchTextOnce(502, '');
  await expect(fetchApiData('/x')).rejects.toMatchObject({ errorCode: 'HTTP_502' });
});

test('네트워크 실패는 APP_NETWORK', async () => {
  fetchMock.mockRejectedValueOnce(new TypeError('Network request failed'));
  await expect(fetchApiData('/x')).rejects.toMatchObject({ errorCode: 'APP_NETWORK' });
});

test('시간 제한을 넘기면 APP_TIMEOUT', async () => {
  jest.useFakeTimers();
  fetchMock.mockImplementationOnce(
    (_url: string, init: { signal: AbortSignal }) =>
      new Promise((_resolve, reject) => {
        init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      }),
  );
  const request = fetchApiData('/x');
  jest.advanceTimersByTime(apiConfig.requestTimeoutMilliseconds);
  await expect(request).rejects.toMatchObject({ errorCode: 'APP_TIMEOUT' });
});
