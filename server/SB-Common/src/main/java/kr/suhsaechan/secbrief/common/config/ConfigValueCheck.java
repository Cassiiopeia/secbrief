package kr.suhsaechan.secbrief.common.config;

import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 설정 값 검사 결과: 통과(정리한 값) 또는 거부(사유 ErrorCode)
 */
public record ConfigValueCheck(String normalizedValue, ErrorCode rejectionCode) {

    public static ConfigValueCheck accepted(String normalizedValue) {
        return new ConfigValueCheck(normalizedValue, null);
    }

    public static ConfigValueCheck rejected(ErrorCode rejectionCode) {
        return new ConfigValueCheck(null, rejectionCode);
    }

    public boolean isAccepted() {
        return rejectionCode == null;
    }
}
