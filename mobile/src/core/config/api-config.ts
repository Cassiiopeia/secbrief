/**
 * 서버 API 주소 설정: 빌드 시 EXPO_PUBLIC_API_BASE_URL 로 주입
 */
// 기본값: 로컬 개발 서버 (Android 에뮬레이터에서 호스트를 가리키는 주소)
const DEFAULT_API_BASE_URL = 'http://10.0.2.2:8080';

export const apiConfig = {
  baseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? DEFAULT_API_BASE_URL,
  // 요청 제한 시간 (단위: 밀리초)
  requestTimeoutMilliseconds: 10_000,
} as const;
