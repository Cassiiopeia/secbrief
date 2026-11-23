import { apiConfig } from '@/core/config/api-config';
import {
  AppFailure,
  INVALID_RESPONSE_ERROR_CODE,
  NETWORK_ERROR_CODE,
  TIMEOUT_ERROR_CODE,
} from '@/core/error/app-failure';

/** 쿼리 값: undefined 는 주소에서 뺌 */
export type ApiQueryParameters = Record<string, string | undefined>;

// 서버 실패 응답 본문 형태 (GlobalExceptionHandler > ErrorResponse)
interface ApiErrorBody {
  errorCode: string;
  message: string;
}

// 오류 본문이 없는 HTTP 실패 코드 접두사 (예: HTTP_502)
const HTTP_ERROR_CODE_PREFIX = 'HTTP_';

/**
 * 서버 조회 공통 (앱의 유일한 외부 통신): 주소 조립 > 시간 제한 요청 > {data} 꺼내기
 * 실패 변환 5종: 시간 초과, 네트워크, 응답 읽기 실패, 서버 오류 본문, 본문 없는 HTTP 오류 > AppFailure
 */
export async function fetchApiData<T>(path: string, queryParameters?: ApiQueryParameters): Promise<T> {
  const abortController = new AbortController();
  const timeoutHandle = setTimeout(() => abortController.abort(), apiConfig.requestTimeoutMilliseconds);
  let response: Response;
  try {
    response = await fetch(toRequestUrl(path, queryParameters), {
      headers: { Accept: 'application/json' },
      signal: abortController.signal,
    });
  } catch {
    throw abortController.signal.aborted
      ? new AppFailure(TIMEOUT_ERROR_CODE, '응답이 늦어요. 잠시 후 다시 시도해 주세요.')
      : new AppFailure(NETWORK_ERROR_CODE, '인터넷 연결을 확인해 주세요.');
  } finally {
    clearTimeout(timeoutHandle); // (성공·실패 공통)
  }

  const body = await readJsonBody(response);
  if (!response.ok) throw toServerFailure(response.status, body);
  if (!isObject(body) || !('data' in body) || body.data === undefined || body.data === null) {
    throw toInvalidResponseFailure();
  }
  return body.data as T;
}

/** 기본 주소 + 경로 + 쿼리 (undefined 값은 뺌) */
function toRequestUrl(path: string, queryParameters?: ApiQueryParameters): string {
  const definedEntries = Object.entries(queryParameters ?? {}).filter(
    (entry): entry is [string, string] => entry[1] !== undefined,
  );
  const queryString = new URLSearchParams(definedEntries).toString();
  return `${apiConfig.baseUrl}${path}${queryString ? `?${queryString}` : ''}`;
}

// 본문 JSON 읽기 (빈 본문·JSON 아님 > undefined)
async function readJsonBody(response: Response): Promise<unknown> {
  try {
    const text = await response.text();
    return text ? JSON.parse(text) : undefined;
  } catch {
    return undefined;
  }
}

// 오류 본문이 있으면 서버 코드·문구 그대로, 없으면 HTTP_상태
function toServerFailure(status: number, body: unknown): AppFailure {
  if (isApiErrorBody(body)) return new AppFailure(body.errorCode, body.message);
  return new AppFailure(`${HTTP_ERROR_CODE_PREFIX}${status}`, '서버에 문제가 생겼어요.');
}

function toInvalidResponseFailure(): AppFailure {
  return new AppFailure(INVALID_RESPONSE_ERROR_CODE, '서버 응답을 읽지 못했어요.');
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isApiErrorBody(body: unknown): body is ApiErrorBody {
  return isObject(body) && typeof body.errorCode === 'string' && typeof body.message === 'string';
}
