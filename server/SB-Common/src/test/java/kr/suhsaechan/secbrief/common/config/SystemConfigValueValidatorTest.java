package kr.suhsaechan.secbrief.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 설정 값 검사: 값 종류별 형식·범위·허용 값·길이, 정리한 값(공백 제거·숫자 표기)과 거부 사유
 */
class SystemConfigValueValidatorTest {

    @Test
    void acceptsIntegerInRangeAndNormalizesIt() {
        assertAccepted(ConfigKey.AI_TITLE_BATCH_SIZE, " 020 ", "20");
        assertAccepted(ConfigKey.AI_TITLE_BATCH_SIZE, "1", "1");
        assertAccepted(ConfigKey.AI_TITLE_BATCH_SIZE, "200", "200");
    }

    @Test
    void rejectsIntegerOutOfRangeOrNotNumber() {
        assertRejected(ConfigKey.AI_TITLE_BATCH_SIZE, "0", ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        assertRejected(ConfigKey.AI_TITLE_BATCH_SIZE, "201", ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        assertRejected(ConfigKey.AI_TITLE_BATCH_SIZE, "2.5", ErrorCode.CONFIG_VALUE_NOT_NUMBER);
        assertRejected(ConfigKey.AI_TITLE_BATCH_SIZE, "스무 건", ErrorCode.CONFIG_VALUE_NOT_NUMBER);
        assertRejected(ConfigKey.AI_TITLE_BATCH_SIZE, "99999999999", ErrorCode.CONFIG_VALUE_NOT_NUMBER);
    }

    @Test
    void rejectsBlankExceptForSecret() {
        assertRejected(ConfigKey.AI_TITLE_BATCH_SIZE, "  ", ErrorCode.CONFIG_VALUE_REQUIRED);
        assertRejected(ConfigKey.AI_PROVIDER_PRIORITY, null, ErrorCode.CONFIG_VALUE_REQUIRED);
        // 비밀값의 빈 값은 "그대로 두기" (저장 쪽에서 처리)
        assertAccepted(ConfigKey.ANTHROPIC_API_KEYS, " ", "");
    }

    @Test
    void acceptsDecimalInRangeIncludingBudgetOffValue() {
        assertAccepted(ConfigKey.AI_DAILY_BUDGET_USD, "-1", "-1");
        assertAccepted(ConfigKey.AI_DAILY_BUDGET_USD, " 2.50 ", "2.50");
        assertAccepted(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M, "0", "0");
        assertAccepted(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M, "1E+1", "10");
    }

    @Test
    void rejectsDecimalOutOfRangeTooPreciseOrNotNumber() {
        assertRejected(ConfigKey.AI_DAILY_BUDGET_USD, "-1.01", ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        assertRejected(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M, "-0.1", ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        assertRejected(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M, "1000.01", ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        assertRejected(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M, "0.1234567", ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        assertRejected(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M, "십 센트", ErrorCode.CONFIG_VALUE_NOT_NUMBER);
    }

    @Test
    void acceptsOnlyTrueOrFalseForBoolean() {
        assertAccepted(ConfigKey.URGENT_ALERT_ENABLED, " TRUE ", "true");
        assertAccepted(ConfigKey.URGENT_ALERT_ENABLED, "false", "false");
        // "on"·"yes" 를 조용히 false 로 읽지 않고 거부
        assertRejected(ConfigKey.URGENT_ALERT_ENABLED, "on", ErrorCode.CONFIG_VALUE_NOT_ALLOWED);
    }

    @Test
    void limitsLengthOfStringAndSecret() {
        assertAccepted(ConfigKey.AI_PROVIDER_PRIORITY, " anthropic,ollama ", "anthropic,ollama");
        assertRejected(ConfigKey.AI_PROVIDER_PRIORITY, "a".repeat(101), ErrorCode.CONFIG_VALUE_TOO_LONG);
        assertRejected(ConfigKey.ANTHROPIC_API_KEYS, "k".repeat(8001), ErrorCode.CONFIG_VALUE_TOO_LONG);
    }

    @Test
    void acceptsOnlyAllowedValuesForEnum() {
        ConfigValueRule enumRule = ConfigValueRule.oneOf("low", "high");

        assertThat(SystemConfigValueValidator.check(enumRule, " high ").normalizedValue()).isEqualTo("high");
        assertThat(SystemConfigValueValidator.check(enumRule, "medium").rejectionCode())
                .isEqualTo(ErrorCode.CONFIG_VALUE_NOT_ALLOWED);
    }

    @Test
    void everyDefaultValuePassesItsOwnRule() {
        for (ConfigKey key : ConfigKey.values()) {
            assertThat(SystemConfigValueValidator.check(key, key.getDefaultValue()).isAccepted())
                    .as("default of %s", key).isTrue();
        }
    }

    private static void assertAccepted(ConfigKey key, String rawValue, String expectedNormalizedValue) {
        ConfigValueCheck check = SystemConfigValueValidator.check(key, rawValue);
        assertThat(check.isAccepted()).isTrue();
        assertThat(check.normalizedValue()).isEqualTo(expectedNormalizedValue);
    }

    private static void assertRejected(ConfigKey key, String rawValue, ErrorCode expectedRejectionCode) {
        ConfigValueCheck check = SystemConfigValueValidator.check(key, rawValue);
        assertThat(check.isAccepted()).isFalse();
        assertThat(check.rejectionCode()).isEqualTo(expectedRejectionCode);
    }
}
