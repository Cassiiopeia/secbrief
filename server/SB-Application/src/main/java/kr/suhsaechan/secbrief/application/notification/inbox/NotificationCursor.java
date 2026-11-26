package kr.suhsaechan.secbrief.application.notification.inbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;
import java.util.Base64;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 알림함 목록 커서: (고정 여부, 만든 시각, 번호) <-> URL-safe Base64 문자열 (원문 "{1|0}:{epochMilli}:{id}", 패딩 없음)
 */
public record NotificationCursor(boolean isPinned, Instant createdAt, long id) {

    private static final String SEPARATOR = ":";
    private static final String PINNED_FLAG = "1";
    private static final String UNPINNED_FLAG = "0";

    public static String encode(boolean isPinned, Instant createdAt, long id) {
        String rawCursor = (isPinned ? PINNED_FLAG : UNPINNED_FLAG) + SEPARATOR + createdAt.toEpochMilli() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawCursor.getBytes(UTF_8));
    }

    /**
     * 깨진 커서는 INVALID_CURSOR
     */
    public static NotificationCursor decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), UTF_8).split(SEPARATOR, -1);
            if (parts.length != 3 || !(PINNED_FLAG.equals(parts[0]) || UNPINNED_FLAG.equals(parts[0]))) {
                throw new CustomException(ErrorCode.INVALID_CURSOR);
            }
            return new NotificationCursor(PINNED_FLAG.equals(parts[0]), Instant.ofEpochMilli(Long.parseLong(parts[1])),
                    Long.parseLong(parts[2]));
        } catch (IllegalArgumentException exception) {
            // Base64 오류·숫자 오류 (NumberFormatException 포함)
            throw new CustomException(ErrorCode.INVALID_CURSOR);
        }
    }
}
