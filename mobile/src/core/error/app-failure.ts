/**
 * 화면에 보여 줄 수 있는 실패 한 건: 에러 코드(제보 추적용) + 사용자 문구
 */
export class AppFailure extends Error {
  /** 서버 ErrorCode 이름 또는 앱 자체 코드 (아래 APP_ 상수) */
  readonly errorCode: string;
  readonly userMessage: string;

  constructor(errorCode: string, userMessage: string) {
    super(`AppFailure(${errorCode}): ${userMessage}`);
    this.name = 'AppFailure';
    this.errorCode = errorCode;
    this.userMessage = userMessage;
  }
}

// 앱 자체 코드: 원인을 알 수 없는 실패
export const UNKNOWN_ERROR_CODE = 'APP_UNKNOWN';
// 앱 자체 코드: 서버에 닿지 못함 (인터넷 끊김 등)
export const NETWORK_ERROR_CODE = 'APP_NETWORK';
// 앱 자체 코드: 요청 제한 시간 초과
export const TIMEOUT_ERROR_CODE = 'APP_TIMEOUT';
// 앱 자체 코드: 서버 응답을 읽지 못함 (JSON 아님, data 없음)
export const INVALID_RESPONSE_ERROR_CODE = 'APP_INVALID_RESPONSE';
// 앱 자체 코드: OS 알림 권한이 꺼져 있음
export const PUSH_PERMISSION_DENIED_ERROR_CODE = 'APP_PUSH_PERMISSION_DENIED';
// 앱 자체 코드: 푸시 토큰을 받지 못함 (네트워크, FCM·APNs 설정 누락)
export const PUSH_TOKEN_ERROR_CODE = 'APP_PUSH_TOKEN';
// 앱 자체 코드: 알림을 쓸 수 없는 환경 (웹, 실기기 아님, 프로젝트 ID 없음)
export const PUSH_UNAVAILABLE_ERROR_CODE = 'APP_PUSH_UNAVAILABLE';
// 앱 자체 코드: 켠 알림 종류 없이 방해 금지 시간을 저장하려 함 (서버에 기기가 없음)
export const QUIET_HOURS_WITHOUT_ALERT_ERROR_CODE = 'APP_QUIET_HOURS_WITHOUT_ALERT';

// 서버 ErrorCode: 찾는 글이 없거나 보관 기간이 지남 (다시 불러도 같아 재시도하지 않음)
export const RESOURCE_NOT_FOUND_ERROR_CODE = 'RESOURCE_NOT_FOUND';
// 그 밖의 실패를 다시 부르는 횟수 (React Query 기본값과 같음)
const MAXIMUM_QUERY_RETRY_COUNT = 3;

/** 조회 재시도 여부: 찾지 못함은 곧바로 실패, 그 밖은 3회까지 */
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  return toAppFailure(error).errorCode !== RESOURCE_NOT_FOUND_ERROR_CODE && failureCount < MAXIMUM_QUERY_RETRY_COUNT;
}

/**
 * 임의의 예외 > AppFailure (이미 AppFailure 면 그대로, 아니면 알 수 없는 실패)
 */
export function toAppFailure(error: unknown): AppFailure {
  if (error instanceof AppFailure) return error;
  return new AppFailure(UNKNOWN_ERROR_CODE, '문제가 생겼어요. 잠시 후 다시 시도해 주세요.');
}
