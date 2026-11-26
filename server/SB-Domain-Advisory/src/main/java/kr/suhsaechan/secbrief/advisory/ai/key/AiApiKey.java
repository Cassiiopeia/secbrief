package kr.suhsaechan.secbrief.advisory.ai.key;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 제공자 API 키 1개 (키 목록 JSON 배열의 원소, 제공자 공통): 화면 요청용 id, 별명, 키 값, 켜짐 여부
 * 키 값이 로그에 찍히지 않게 toString 은 값을 뺌
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiApiKey(@JsonProperty("id") String id,
                       @JsonProperty("nickname") String nickname,
                       @JsonProperty("apiKey") String apiKey,
                       @JsonProperty("isEnabled") boolean isEnabled) {

    public AiApiKey withNickname(String newNickname) {
        return new AiApiKey(id, newNickname, apiKey, isEnabled);
    }

    public AiApiKey withApiKey(String newApiKey) {
        return new AiApiKey(id, nickname, newApiKey, isEnabled);
    }

    public AiApiKey withEnabled(boolean isNowEnabled) {
        return new AiApiKey(id, nickname, apiKey, isNowEnabled);
    }

    @Override
    public String toString() {
        return "AiApiKey[id=" + id + ", isEnabled=" + isEnabled + "]";
    }
}
