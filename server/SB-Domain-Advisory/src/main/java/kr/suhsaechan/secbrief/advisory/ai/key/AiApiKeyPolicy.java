package kr.suhsaechan.secbrief.advisory.ai.key;

import java.util.List;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 키 목록 입력 규칙 (제공자 공통): 개수 상한, 별명(길이·중복), 키 값(길이·공백·중복). 거부 사유는 ErrorCode 로 구분
 */
public final class AiApiKeyPolicy {

    // 키 개수 상한 (비밀값 길이 상한 안에 들어오게)
    public static final int MAXIMUM_KEY_COUNT = 10;
    // 별명 최대 길이 (화면 한 줄, ai_call_log.key_label 50자 안)
    public static final int MAXIMUM_NICKNAME_LENGTH = 30;
    // 키 값 길이 범위 (제공자 공통)
    public static final int MINIMUM_API_KEY_LENGTH = 20;
    public static final int MAXIMUM_API_KEY_LENGTH = 300;

    private AiApiKeyPolicy() {
    }

    public static void requireRoomForNewKey(List<AiApiKey> keys) {
        if (keys.size() >= MAXIMUM_KEY_COUNT) {
            throw new CustomException(ErrorCode.AI_KEY_LIMIT_EXCEEDED);
        }
    }

    /** 별명 정리·검사 (ownKeyId: 자기 자신은 중복 검사에서 뺌, 새 키면 null) */
    public static String toAcceptedNickname(String nickname, List<AiApiKey> keys, String ownKeyId) {
        String trimmedNickname = nickname == null ? "" : nickname.strip();
        if (trimmedNickname.isEmpty() || trimmedNickname.length() > MAXIMUM_NICKNAME_LENGTH) {
            throw new CustomException(ErrorCode.AI_KEY_INVALID_NICKNAME);
        }
        boolean isDuplicated = keys.stream()
                .anyMatch(key -> !key.id().equals(ownKeyId) && key.nickname().equals(trimmedNickname));
        if (isDuplicated) {
            throw new CustomException(ErrorCode.AI_KEY_DUPLICATED_NICKNAME);
        }
        return trimmedNickname;
    }

    /** 키 값 정리·검사 (예외 메시지에 키 값을 넣지 않음) */
    public static String toAcceptedApiKey(String apiKey, List<AiApiKey> keys, String ownKeyId) {
        String trimmedApiKey = apiKey == null ? "" : apiKey.strip();
        boolean hasValidLength = trimmedApiKey.length() >= MINIMUM_API_KEY_LENGTH
                && trimmedApiKey.length() <= MAXIMUM_API_KEY_LENGTH;
        if (!hasValidLength || trimmedApiKey.chars().anyMatch(Character::isWhitespace)) {
            throw new CustomException(ErrorCode.AI_KEY_INVALID_VALUE);
        }
        boolean isDuplicated = keys.stream()
                .anyMatch(key -> !key.id().equals(ownKeyId) && key.apiKey().equals(trimmedApiKey));
        if (isDuplicated) {
            throw new CustomException(ErrorCode.AI_KEY_DUPLICATED);
        }
        return trimmedApiKey;
    }
}
