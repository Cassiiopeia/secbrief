package kr.suhsaechan.secbrief.advisory.constant;

import java.util.Arrays;
import java.util.Optional;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 키 목록으로 부르는 AI 제공자 3개 (선언 순서 = 화면 순서): 제공자 코드, 화면 이름, 설정 키(키 목록·모델·단가), 운영 설정 키
 * 자체 서버(Ollama)는 키 목록이 없어 여기 없음
 */
@Getter
@RequiredArgsConstructor
public enum KeyedAiProvider {
    ANTHROPIC(AiProviderCode.ANTHROPIC, "Anthropic", ConfigKey.ANTHROPIC_API_KEYS, null,
            ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M, ConfigKey.PRICE_ANTHROPIC_OUTPUT_PER_1M,
            "secbrief.ai.anthropic-api-key", "sk-ant-..."),
    OPENAI(AiProviderCode.OPENAI, "OpenAI", ConfigKey.OPENAI_API_KEYS, ConfigKey.OPENAI_MODEL,
            ConfigKey.PRICE_OPENAI_INPUT_PER_1M, ConfigKey.PRICE_OPENAI_OUTPUT_PER_1M,
            null, "sk-..."),
    GOOGLE(AiProviderCode.GOOGLE, "Google", ConfigKey.GOOGLE_API_KEYS, ConfigKey.GOOGLE_MODEL,
            ConfigKey.PRICE_GOOGLE_INPUT_PER_1M, ConfigKey.PRICE_GOOGLE_OUTPUT_PER_1M,
            null, "Gemini API 키");

    private final String providerCode;
    private final String displayName;
    // 키 목록 비밀값: [{"id","nickname","apiKey","isEnabled"}] JSON 배열 (배열 순서 = 우선순위)
    private final ConfigKey apiKeysConfigKey;
    // 모델 설정 키 (null: 운영 설정 secbrief.ai.model 로 고정)
    private final ConfigKey modelConfigKey;
    private final ConfigKey inputPriceConfigKey;
    private final ConfigKey outputPriceConfigKey;
    // 켜진 키가 없을 때 쓰는 운영 설정 키 이름 (쉼표로 여러 개, null: 없음)
    private final String deployPropertyName;
    // 키 입력 칸 안내 글
    private final String apiKeyPlaceholder;

    /** 제공자 코드 > 제공자 (키 목록이 없는 제공자·모르는 코드는 빈 값) */
    public static Optional<KeyedAiProvider> fromProviderCode(String providerCode) {
        return Arrays.stream(values())
                .filter(provider -> provider.providerCode.equals(providerCode))
                .findFirst();
    }

    public boolean hasFixedModel() {
        return modelConfigKey == null;
    }
}
