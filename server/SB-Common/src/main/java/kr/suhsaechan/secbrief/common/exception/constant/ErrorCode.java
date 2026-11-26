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
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "목록 위치 값이 올바르지 않아요."),

    // 관리자 페이지 (계정, 출처 관리)
    ADMIN_CURRENT_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 맞지 않아요."),
    ADMIN_PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "새 비밀번호는 10자 이상으로, 영문과 숫자를 함께 써 주세요."),
    ADMIN_PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "새 비밀번호와 확인 값이 서로 달라요."),
    ADMIN_PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "지금 쓰는 비밀번호와 다른 값으로 정해 주세요."),
    SOURCE_INTERVAL_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "수집 주기는 5분에서 1440분 사이로 정해 주세요."),

    // 외부 연동 (보안 공지 출처, AI 제공자)
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 서비스 응답에 문제가 있어요."),
    FEED_PARSE_ERROR(HttpStatus.BAD_GATEWAY, "외부 소식 형식을 읽지 못했어요."),
    AI_API_ERROR(HttpStatus.BAD_GATEWAY, "AI 처리에 문제가 있어요."),

    // 자체 저장소 (기사 대표 이미지)
    IMAGE_STORAGE_ERROR(HttpStatus.BAD_GATEWAY, "이미지를 불러오지 못했어요."),

    // 관리자 설정 저장소 (system_config)
    CONFIG_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 설정이에요."),
    CONFIG_VALUE_REQUIRED(HttpStatus.BAD_REQUEST, "값을 입력해 주세요."),
    CONFIG_VALUE_NOT_NUMBER(HttpStatus.BAD_REQUEST, "숫자로 입력해 주세요."),
    CONFIG_VALUE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "허용 범위 안의 값을 입력해 주세요."),
    CONFIG_VALUE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "고를 수 있는 값이 아니에요."),
    CONFIG_VALUE_TOO_LONG(HttpStatus.BAD_REQUEST, "값이 너무 길어요."),
    CONFIG_NOT_EDITABLE_HERE(HttpStatus.BAD_REQUEST, "이 설정은 전용 화면에서 바꿔 주세요."),
    SECRET_MASTER_KEY_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "암호화 마스터 키가 없어 비밀값을 저장할 수 없어요."),

    // 관리자 AI 키 · 제공자
    AI_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 키예요. 화면을 새로 고쳐 주세요."),
    AI_KEY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "등록할 수 있는 키 개수를 넘었어요."),
    AI_KEY_INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "별명 길이를 다시 확인해 주세요."),
    AI_KEY_DUPLICATED_NICKNAME(HttpStatus.BAD_REQUEST, "같은 별명의 키가 이미 있어요."),
    AI_KEY_INVALID_VALUE(HttpStatus.BAD_REQUEST, "키 값을 공백 없이 통째로 붙여 넣어 주세요."),
    AI_KEY_DUPLICATED(HttpStatus.BAD_REQUEST, "이미 등록한 키예요."),
    AI_PROVIDER_UNKNOWN(HttpStatus.BAD_REQUEST, "모르는 AI 제공자예요.");

    private final HttpStatus status;
    private final String message;
}
