package kr.suhsaechan.secbrief.advisory.ai.calllog;

import java.util.Locale;
import lombok.RequiredArgsConstructor;

/**
 * AI 호출 실패 종류 (ai_call_log.failure_reason 값 = 이름): 화면 표시 이름, 다른 키로 바꾸면 나을 수 있는지
 * 다음 키로 넘김: 401·402·403·429·529, 5xx, 연결 실패·시간 초과, 크레딧 부족(400 + credit balance). 그 밖은 바로 실패
 */
@RequiredArgsConstructor
public enum AiFailureKind {
    UNAUTHORIZED("인증 실패 (401)", true),
    PAYMENT_REQUIRED("결제 필요 (402)", true),
    PERMISSION_DENIED("권한 없음 (403)", true),
    RATE_LIMITED("한도 초과 (429)", true),
    OVERLOADED("과부하 (529)", true),
    SERVER_ERROR("제공자 서버 오류 (5xx)", true),
    CONNECTION_FAILED("연결 실패 · 시간 초과", true),
    CREDIT_EXHAUSTED("크레딧 부족", true),
    BAD_REQUEST("잘못된 요청 (400)", false),
    NOT_FOUND("대상 없음 (404)", false),
    CLIENT_ERROR("요청 오류 (4xx)", false),
    INVALID_CONFIGURATION("설정 오류", false),
    UNKNOWN("알 수 없는 오류", false);

    private static final int BAD_REQUEST_STATUS = 400;
    private static final int UNAUTHORIZED_STATUS = 401;
    private static final int PAYMENT_REQUIRED_STATUS = 402;
    private static final int PERMISSION_DENIED_STATUS = 403;
    private static final int NOT_FOUND_STATUS = 404;
    private static final int RATE_LIMITED_STATUS = 429;
    private static final int MINIMUM_SERVER_ERROR_STATUS = 500;
    private static final int OVERLOADED_STATUS = 529;
    private static final int MAXIMUM_STATUS = 599;
    // 크레딧 소진은 400 invalid_request_error 로 옴 ("Your credit balance is too low ...")
    private static final String CREDIT_BALANCE_MARKER = "credit balance";

    private final String label;
    private final boolean shouldTryNextKey;

    public String getLabel() {
        return label;
    }

    /** 다른 키로 바꾸면 나을 수 있는 실패인지 */
    public boolean shouldTryNextKey() {
        return shouldTryNextKey;
    }

    /**
     * HTTP 상태 코드 + 오류 본문 > 실패 종류 (errorText 는 판정에만 쓰고 어디에도 남기지 않음)
     */
    public static AiFailureKind fromStatusCode(int statusCode, String errorText) {
        return switch (statusCode) {
            case BAD_REQUEST_STATUS -> hasCreditBalanceMarker(errorText) ? CREDIT_EXHAUSTED : BAD_REQUEST;
            case UNAUTHORIZED_STATUS -> UNAUTHORIZED;
            case PAYMENT_REQUIRED_STATUS -> PAYMENT_REQUIRED;
            case PERMISSION_DENIED_STATUS -> PERMISSION_DENIED;
            case NOT_FOUND_STATUS -> NOT_FOUND;
            case RATE_LIMITED_STATUS -> RATE_LIMITED;
            case OVERLOADED_STATUS -> OVERLOADED;
            default -> fromStatusRange(statusCode);
        };
    }

    private static AiFailureKind fromStatusRange(int statusCode) {
        if (statusCode >= MINIMUM_SERVER_ERROR_STATUS && statusCode <= MAXIMUM_STATUS) {
            return SERVER_ERROR;
        }
        return statusCode > BAD_REQUEST_STATUS && statusCode < MINIMUM_SERVER_ERROR_STATUS ? CLIENT_ERROR : UNKNOWN;
    }

    private static boolean hasCreditBalanceMarker(String errorText) {
        return errorText != null && errorText.toLowerCase(Locale.ROOT).contains(CREDIT_BALANCE_MARKER);
    }
}
