package kr.suhsaechan.secbrief.common.config;

import java.math.BigDecimal;
import java.util.List;

/**
 * 설정 값 규칙: 값 종류, 숫자 범위(INT·DECIMAL), 허용 값(ENUM), 최대 길이(STRING·SECRET)
 * 해당 없는 항목은 null·빈 목록·0
 */
public record ConfigValueRule(ConfigValueType valueType, BigDecimal minimumValue, BigDecimal maximumValue,
                              List<String> allowedValues, int maximumLength) {

    public static ConfigValueRule integerBetween(int minimumValue, int maximumValue) {
        return new ConfigValueRule(ConfigValueType.INT, BigDecimal.valueOf(minimumValue),
                BigDecimal.valueOf(maximumValue), List.of(), 0);
    }

    public static ConfigValueRule decimalBetween(String minimumValue, String maximumValue) {
        return new ConfigValueRule(ConfigValueType.DECIMAL, new BigDecimal(minimumValue),
                new BigDecimal(maximumValue), List.of(), 0);
    }

    public static ConfigValueRule toggle() {
        return new ConfigValueRule(ConfigValueType.BOOLEAN, null, null, List.of(), 0);
    }

    public static ConfigValueRule text(int maximumLength) {
        return new ConfigValueRule(ConfigValueType.STRING, null, null, List.of(), maximumLength);
    }

    public static ConfigValueRule oneOf(String... allowedValues) {
        return new ConfigValueRule(ConfigValueType.ENUM, null, null, List.of(allowedValues), 0);
    }

    public static ConfigValueRule secret(int maximumLength) {
        return new ConfigValueRule(ConfigValueType.SECRET, null, null, List.of(), maximumLength);
    }
}
