package kr.suhsaechan.secbrief.common.config;

import java.math.BigDecimal;
import java.util.Locale;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 설정 값 검사 (저장 전, 규칙 한 곳): 값 종류별 형식·범위·허용 값·길이 > 정리한 값 또는 거부 사유
 */
public final class SystemConfigValueValidator {

    // DECIMAL 소수 자리 상한 (ai_call_log.estimated_cost_usd 와 같은 자리)
    private static final int MAXIMUM_DECIMAL_SCALE = 6;

    private SystemConfigValueValidator() {
    }

    public static ConfigValueCheck check(ConfigKey key, String rawValue) {
        return check(key.getValueRule(), rawValue);
    }

    /**
     * 값 규칙 1개로 검사. SECRET 의 빈 값은 통과 (저장 쪽에서 "그대로 두기"로 처리)
     */
    public static ConfigValueCheck check(ConfigValueRule valueRule, String rawValue) {
        String trimmedValue = rawValue == null ? "" : rawValue.strip();
        if (trimmedValue.isEmpty()) {
            return valueRule.valueType() == ConfigValueType.SECRET
                    ? ConfigValueCheck.accepted("")
                    : ConfigValueCheck.rejected(ErrorCode.CONFIG_VALUE_REQUIRED);
        }
        return switch (valueRule.valueType()) {
            case INT -> checkInteger(valueRule, trimmedValue);
            case DECIMAL -> checkDecimal(valueRule, trimmedValue);
            case BOOLEAN -> checkBoolean(trimmedValue);
            case ENUM -> valueRule.allowedValues().contains(trimmedValue)
                    ? ConfigValueCheck.accepted(trimmedValue)
                    : ConfigValueCheck.rejected(ErrorCode.CONFIG_VALUE_NOT_ALLOWED);
            case STRING, SECRET -> trimmedValue.length() > valueRule.maximumLength()
                    ? ConfigValueCheck.rejected(ErrorCode.CONFIG_VALUE_TOO_LONG)
                    : ConfigValueCheck.accepted(trimmedValue);
        };
    }

    private static ConfigValueCheck checkInteger(ConfigValueRule valueRule, String trimmedValue) {
        int number;
        try {
            number = Integer.parseInt(trimmedValue);
        } catch (NumberFormatException exception) {
            return ConfigValueCheck.rejected(ErrorCode.CONFIG_VALUE_NOT_NUMBER);
        }
        return isOutOfRange(valueRule, BigDecimal.valueOf(number))
                ? ConfigValueCheck.rejected(ErrorCode.CONFIG_VALUE_OUT_OF_RANGE)
                : ConfigValueCheck.accepted(String.valueOf(number));
    }

    private static ConfigValueCheck checkDecimal(ConfigValueRule valueRule, String trimmedValue) {
        BigDecimal number;
        try {
            number = new BigDecimal(trimmedValue);
        } catch (NumberFormatException exception) {
            return ConfigValueCheck.rejected(ErrorCode.CONFIG_VALUE_NOT_NUMBER);
        }
        if (isOutOfRange(valueRule, number) || number.stripTrailingZeros().scale() > MAXIMUM_DECIMAL_SCALE) {
            return ConfigValueCheck.rejected(ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        }
        return ConfigValueCheck.accepted(number.toPlainString());
    }

    private static ConfigValueCheck checkBoolean(String trimmedValue) {
        String lowerCaseValue = trimmedValue.toLowerCase(Locale.ROOT);
        return "true".equals(lowerCaseValue) || "false".equals(lowerCaseValue)
                ? ConfigValueCheck.accepted(lowerCaseValue)
                : ConfigValueCheck.rejected(ErrorCode.CONFIG_VALUE_NOT_ALLOWED);
    }

    /** 범위 밖인지 (범위가 없는 규칙은 항상 false) */
    public static boolean isOutOfRange(ConfigValueRule valueRule, BigDecimal number) {
        return (valueRule.minimumValue() != null && number.compareTo(valueRule.minimumValue()) < 0)
                || (valueRule.maximumValue() != null && number.compareTo(valueRule.maximumValue()) > 0);
    }
}
