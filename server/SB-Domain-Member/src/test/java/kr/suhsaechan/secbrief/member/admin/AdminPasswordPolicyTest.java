package kr.suhsaechan.secbrief.member.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 관리자 비밀번호 규칙: 10자 이상, 영문·숫자 포함, BCrypt 한도(72바이트) 이하
 */
class AdminPasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh12", "Sample-Value-2026", "가나다라마바사abc1"})
    void acceptsLongEnoughWithLetterAndDigit(String password) {
        assertThat(AdminPasswordPolicy.isAcceptable(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc123", "abcdefgh1", "abcdefghijk", "12345678901", "가나다라마바사아자차1"})
    void rejectsShortOrMissingLetterOrDigit(String password) {
        assertThat(AdminPasswordPolicy.isAcceptable(password)).isFalse();
    }

    @Test
    void rejectsNullAndTooLongForBcrypt() {
        assertThat(AdminPasswordPolicy.isAcceptable(null)).isFalse();
        assertThat(AdminPasswordPolicy.isAcceptable("a1" + "x".repeat(AdminPasswordPolicy.MAXIMUM_BYTE_LENGTH))).isFalse();
        assertThat(AdminPasswordPolicy.isAcceptable("a1" + "x".repeat(AdminPasswordPolicy.MAXIMUM_BYTE_LENGTH - 2))).isTrue();
    }
}
