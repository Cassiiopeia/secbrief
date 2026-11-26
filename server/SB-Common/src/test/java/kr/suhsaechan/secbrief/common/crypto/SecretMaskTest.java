package kr.suhsaechan.secbrief.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 비밀값 가리기: •••• + 끝 4자, 짧은 값은 끝자리도 가림, 없으면 빈 문자열
 */
class SecretMaskTest {

    @Test
    void showsOnlyLastFourCharacters() {
        assertThat(SecretMask.mask("sk-test-first-000000000000-AAAA")).isEqualTo("••••AAAA");
    }

    @Test
    void hidesTailOfShortValues() {
        assertThat(SecretMask.mask("short-key")).isEqualTo("••••");
        assertThat(SecretMask.mask("12345678901")).isEqualTo("••••");
        assertThat(SecretMask.mask("123456789012")).isEqualTo("••••9012");
    }

    @Test
    void returnsEmptyForMissingValue() {
        assertThat(SecretMask.mask(null)).isEmpty();
        assertThat(SecretMask.mask("")).isEmpty();
    }
}
