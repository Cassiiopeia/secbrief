package kr.suhsaechan.secbrief.member.admin;

import java.nio.charset.StandardCharsets;

/**
 * 관리자 비밀번호 규칙: 10자 이상 72바이트 이하, 영문·숫자 포함
 */
public final class AdminPasswordPolicy {

    public static final int MINIMUM_LENGTH = 10;
    // BCrypt 가 읽는 최대 길이 (72바이트): 넘는 부분은 대조에 쓰이지 않음
    public static final int MAXIMUM_BYTE_LENGTH = 72;

    private AdminPasswordPolicy() {
    }

    public static boolean isAcceptable(String password) {
        if (password == null || password.length() < MINIMUM_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BYTE_LENGTH) {
            return false;
        }
        return password.chars().anyMatch(AdminPasswordPolicy::isAsciiLetter)
                && password.chars().anyMatch(AdminPasswordPolicy::isAsciiDigit);
    }

    private static boolean isAsciiLetter(int character) {
        return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
    }

    private static boolean isAsciiDigit(int character) {
        return character >= '0' && character <= '9';
    }
}
