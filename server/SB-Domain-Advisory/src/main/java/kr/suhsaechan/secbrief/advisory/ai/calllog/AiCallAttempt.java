package kr.suhsaechan.secbrief.advisory.ai.calllog;

/**
 * 제공자 호출 1회 (키 1개로 1번): 용도, 제공자 코드, 모델, 키 별명(키 값 아님, 키를 안 쓰면 null), 걸린 시간
 */
public record AiCallAttempt(AiCallType callType, String providerCode, String model, String keyLabel,
                            long latencyMilliseconds) {
}
