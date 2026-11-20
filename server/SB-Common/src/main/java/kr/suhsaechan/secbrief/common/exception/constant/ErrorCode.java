package kr.suhsaechan.secbrief.common.exception.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드 단일 원본: 이름이 그대로 응답 errorCode (앱이 파싱)
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 전역
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼어요."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청을 다시 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근할 수 없어요."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 내용을 찾을 수 없어요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식이에요."),

    // 요청 형식 (프레임워크 예외 변환용)
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 다시 확인해 주세요."),
    MESSAGE_NOT_READABLE(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없어요."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "필수 값이 빠졌어요."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "값의 형식이 맞지 않아요."),

    // 외부 연동 (보안 공지 출처, AI 제공자)
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 응답에 문제가 있어요.");

    private final HttpStatus status;
    private final String message;
}
