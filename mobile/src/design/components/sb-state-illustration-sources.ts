import { NETWORK_ERROR_CODE, TIMEOUT_ERROR_CODE } from '@/core/error/app-failure';

/**
 * 상태 그림 8종 (빈 상태·오류): inboxEmpty 받은 알림 없음(종), noticeEmpty 공지 없음(확성기), searchIdle 검색 전(돋보기),
 * searchEmpty 검색 결과 없음(빈 쟁반), feedEmpty 새 소식 없음(신문), notFound 찾지 못함(빈 상자), error 문제 생김(엉킨 리본), offline 연결 끊김(뽑힌 플러그)
 */
export type SbStateIllustrationKind =
  | 'inboxEmpty'
  | 'noticeEmpty'
  | 'searchIdle'
  | 'searchEmpty'
  | 'feedEmpty'
  | 'notFound'
  | 'error'
  | 'offline';

// 그림 파일 단일 원본 (require 는 번들 자산 번호): assets/illustrations (밝은·어두운 바탕 공용 투명 WebP, 408px = 표시 크기 3배)
export const SB_STATE_ILLUSTRATION_SOURCES: Record<SbStateIllustrationKind, number> = {
  inboxEmpty: require('@/assets/illustrations/state-inbox-empty.webp'),
  noticeEmpty: require('@/assets/illustrations/state-notice-empty.webp'),
  searchIdle: require('@/assets/illustrations/state-search-idle.webp'),
  searchEmpty: require('@/assets/illustrations/state-search-empty.webp'),
  feedEmpty: require('@/assets/illustrations/state-feed-empty.webp'),
  notFound: require('@/assets/illustrations/state-not-found.webp'),
  error: require('@/assets/illustrations/state-error.webp'),
  offline: require('@/assets/illustrations/state-offline.webp'),
};

// 연결 끊김 그림을 쓰는 에러 코드 2개: 서버에 닿지 못함, 시간 초과
const OFFLINE_ERROR_CODES: readonly string[] = [NETWORK_ERROR_CODE, TIMEOUT_ERROR_CODE];

/** 에러 코드 > 오류 그림 (연결 문제면 offline, 그 밖은 error) */
export function toSbErrorIllustrationKind(errorCode: string): SbStateIllustrationKind {
  return OFFLINE_ERROR_CODES.includes(errorCode) ? 'offline' : 'error';
}
