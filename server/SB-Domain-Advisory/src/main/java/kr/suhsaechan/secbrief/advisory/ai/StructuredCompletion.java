package kr.suhsaechan.secbrief.advisory.ai;

/**
 * 제공자 응답 1회분: 응답 JSON 문자열, 출력 토큰 상한에 걸려 잘렸는지, 제공자가 답을 거부했는지
 */
public record StructuredCompletion(String responseJson, boolean isTruncated, boolean isRefused) {

    public static StructuredCompletion of(String responseJson, boolean isTruncated) {
        return new StructuredCompletion(responseJson, isTruncated, false);
    }

    /** 거부: 응답 없음 (입력 내용 문제라 호출부가 건별 실패로 처리) */
    public static StructuredCompletion refused() {
        return new StructuredCompletion(null, false, true);
    }
}
