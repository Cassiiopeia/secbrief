package kr.suhsaechan.secbrief.common.exception;

import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 전역 단일 업무 예외: 도메인별 예외 클래스 없이 ErrorCode 로 구분
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;
    // 로그용 부가 정보 (영어, 없으면 null)
    private final String detail;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    /** detail: 로그용 부가 정보 (응답에는 ErrorCode 기본 문구만 나감) */
    public CustomException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + " - " + detail);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public HttpStatus getStatus() {
        return errorCode.getStatus();
    }
}
