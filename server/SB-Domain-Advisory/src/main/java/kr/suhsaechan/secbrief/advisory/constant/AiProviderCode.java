package kr.suhsaechan.secbrief.advisory.constant;

/**
 * AI 제공자 코드 단일 원본 (설정 값 AI_PROVIDER_PRIORITY·secbrief.ai.provider, ai_call_log.provider)
 */
public final class AiProviderCode {

    public static final String ANTHROPIC = "anthropic";
    public static final String OPENAI = "openai";
    public static final String GOOGLE = "google";
    public static final String OLLAMA = "ollama";

    private AiProviderCode() {
    }
}
