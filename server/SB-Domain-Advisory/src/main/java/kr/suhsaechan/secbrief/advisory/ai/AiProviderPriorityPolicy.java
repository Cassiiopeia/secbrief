package kr.suhsaechan.secbrief.advisory.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 제공자 우선순위 값 규칙: "anthropic,ollama" > [anthropic, ollama] (공백 제거, 소문자, 빈 칸·중복 버림)
 */
public final class AiProviderPriorityPolicy {

    public static final String DELIMITER = ",";
    // 로그에 남길 설정값 최대 길이
    private static final int MAXIMUM_LOGGED_LENGTH = 30;

    private AiProviderPriorityPolicy() {
    }

    /** 설정 값 > 제공자 코드 목록 (우선순위 순) */
    public static List<String> toProviderCodes(String priorityValue) {
        if (priorityValue == null) {
            return List.of();
        }
        return Arrays.stream(priorityValue.split(DELIMITER))
                .map(providerCode -> providerCode.strip().toLowerCase(Locale.ROOT))
                .filter(providerCode -> !providerCode.isEmpty())
                .distinct()
                .toList();
    }

    /** 제공자 코드 목록 > 설정 값 */
    public static String toPriorityValue(List<String> providerCodes) {
        return String.join(DELIMITER, providerCodes);
    }

    /** 로그용 값 정리 (영문·숫자·-·_·, 외 > ?, 30자 제한) */
    public static String toLoggableValue(String priorityValue) {
        String sanitizedValue = (priorityValue == null ? "" : priorityValue.strip()).replaceAll("[^A-Za-z0-9_,-]", "?");
        return sanitizedValue.length() > MAXIMUM_LOGGED_LENGTH
                ? sanitizedValue.substring(0, MAXIMUM_LOGGED_LENGTH)
                : sanitizedValue;
    }
}
