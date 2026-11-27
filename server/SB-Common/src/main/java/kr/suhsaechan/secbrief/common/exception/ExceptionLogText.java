package kr.suhsaechan.secbrief.common.exception;

import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.common.text.TextTruncation;

/**
 * 예외 > 로그·기록용 글 단일 지점: 제어문자 정리, 길이 제한, CustomException detail 우선
 */
public final class ExceptionLogText {

    // collect_run.error_message 열 길이 (로그 원인 글도 같은 상한)
    public static final int MAXIMUM_ERROR_MESSAGE_LENGTH = 500;
    private static final String UNKNOWN_ERROR_MESSAGE = "unknown";
    // 유니코드 제어문자 (줄바꿈·NUL 등) > ? (로그 줄 위조 방지)
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("\\p{Cc}");
    private static final String CONTROL_CHARACTER_REPLACEMENT = "?";

    private ExceptionLogText() {
    }

    /**
     * 오류 메시지 정리 (제어문자 > ?, 500자 제한, null > unknown)
     */
    public static String toSanitizedErrorMessage(String rawMessage) {
        if (rawMessage == null) {
            return UNKNOWN_ERROR_MESSAGE;
        }
        String withoutControlCharacters = CONTROL_CHARACTER.matcher(rawMessage).replaceAll(CONTROL_CHARACTER_REPLACEMENT);
        return TextTruncation.truncateKeepingSurrogatePairs(withoutControlCharacters, MAXIMUM_ERROR_MESSAGE_LENGTH);
    }

    /**
     * 예외 > 로그 원인 글 (CustomException detail 정리본, 없으면 예외 클래스 이름)
     */
    public static String toLogCause(RuntimeException exception) {
        if (exception instanceof CustomException customException && customException.getDetail() != null) {
            return toSanitizedErrorMessage(customException.getDetail());
        }
        return exception.getClass().getSimpleName();
    }
}
