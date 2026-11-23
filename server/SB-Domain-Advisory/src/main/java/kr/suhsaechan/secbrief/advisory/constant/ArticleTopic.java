package kr.suhsaechan.secbrief.advisory.constant;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 기사 주제 8종: 서버 이름(UPPER_SNAKE) <-> AI JSON·앱 값(lowerCamel)
 */
@Getter
@RequiredArgsConstructor
public enum ArticleTopic {
    VULNERABILITY("vulnerability"),
    RANSOMWARE("ransomware"),
    PRIVACY("privacy"),
    REMOTE_ACCESS("remoteAccess"),
    DATABASE("database"),
    SUPPLY_CHAIN("supplyChain"),
    POLICY("policy"),
    // 분류 전·분류 실패 기본값
    GENERAL("general");

    private final String apiValue;

    /**
     * AI JSON 값 > 주제 (8종 밖의 값은 빈 값)
     */
    public static Optional<ArticleTopic> fromApiValue(String apiValue) {
        return Arrays.stream(values())
                .filter(topic -> topic.apiValue.equals(apiValue))
                .findFirst();
    }
}
