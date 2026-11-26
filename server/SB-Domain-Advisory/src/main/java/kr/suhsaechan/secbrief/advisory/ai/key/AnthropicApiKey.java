package kr.suhsaechan.secbrief.advisory.ai.key;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic API 키 1개 (ANTHROPIC_API_KEYS JSON 배열의 원소): 화면 요청용 id, 별명, 키 값, 켜짐 여부
 * 키 값이 로그에 찍히지 않게 toString 은 값을 뺌
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicApiKey(@JsonProperty("id") String id,
                              @JsonProperty("nickname") String nickname,
                              @JsonProperty("apiKey") String apiKey,
                              @JsonProperty("isEnabled") boolean isEnabled) {

    public AnthropicApiKey withNickname(String newNickname) {
        return new AnthropicApiKey(id, newNickname, apiKey, isEnabled);
    }

    public AnthropicApiKey withApiKey(String newApiKey) {
        return new AnthropicApiKey(id, nickname, newApiKey, isEnabled);
    }

    public AnthropicApiKey withEnabled(boolean isNowEnabled) {
        return new AnthropicApiKey(id, nickname, apiKey, isNowEnabled);
    }

    @Override
    public String toString() {
        return "AnthropicApiKey[id=" + id + ", isEnabled=" + isEnabled + "]";
    }
}
