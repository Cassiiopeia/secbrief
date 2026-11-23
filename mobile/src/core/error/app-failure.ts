/**
 * 화면에 보여 줄 수 있는 실패 한 건: 에러 코드(제보 추적용) + 사용자 문구
 */
export class AppFailure extends Error {
  /** 서버 ErrorCode 이름 또는 앱 자체 코드 (APP_NETWORK, APP_TIMEOUT, APP_INVALID_RESPONSE, APP_UNKNOWN) */
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

/**
 * 임의의 예외 > AppFailure (이미 AppFailure 면 그대로, 아니면 알 수 없는 실패)
 */
export function toAppFailure(error: unknown): AppFailure {
  if (error instanceof AppFailure) return error;
  return new AppFailure(UNKNOWN_ERROR_CODE, '문제가 생겼어요. 잠시 후 다시 시도해 주세요.');
}
