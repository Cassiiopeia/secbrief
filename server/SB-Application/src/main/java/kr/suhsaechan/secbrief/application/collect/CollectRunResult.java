package kr.suhsaechan.secbrief.application.collect;

import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;

/**
 * 출처 1곳 수집 결과: 상태, 새로 저장한 건수, 에러 코드 (성공이면 null)
 */
public record CollectRunResult(CollectStatus status, int newCount, String errorCode) {
}
