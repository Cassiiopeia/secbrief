package kr.suhsaechan.secbrief.advisory.ai.calllog;

/**
 * 호출 1회의 토큰 사용량: 입력 토큰, 출력 토큰 (제공자가 알려 주지 않으면 null)
 */
public record AiTokenUsage(Integer inputTokens, Integer outputTokens) {

    public static final AiTokenUsage UNKNOWN = new AiTokenUsage(null, null);

    public boolean isKnown() {
        return inputTokens != null || outputTokens != null;
    }
}
