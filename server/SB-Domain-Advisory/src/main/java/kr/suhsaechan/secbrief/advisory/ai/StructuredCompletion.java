package kr.suhsaechan.secbrief.advisory.ai;

import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;

/**
 * 제공자 응답 1회분: 응답 JSON 문자열, 출력 토큰 상한에 걸려 잘렸는지, 제공자가 답을 거부했는지, 토큰 사용량
 */
public record StructuredCompletion(String responseJson, boolean isTruncated, boolean isRefused, AiTokenUsage tokenUsage) {

    /** 사용량을 모르는 응답 */
    public static StructuredCompletion of(String responseJson, boolean isTruncated) {
        return new StructuredCompletion(responseJson, isTruncated, false, AiTokenUsage.UNKNOWN);
    }

    public static StructuredCompletion of(String responseJson, boolean isTruncated, AiTokenUsage tokenUsage) {
        return new StructuredCompletion(responseJson, isTruncated, false, tokenUsage);
    }

    /** 거부: 응답 없음 (입력 내용 문제라 호출부가 건별 실패로 처리) */
    public static StructuredCompletion refused() {
        return new StructuredCompletion(null, false, true, AiTokenUsage.UNKNOWN);
    }

    public static StructuredCompletion refused(AiTokenUsage tokenUsage) {
        return new StructuredCompletion(null, false, true, tokenUsage);
    }
}
