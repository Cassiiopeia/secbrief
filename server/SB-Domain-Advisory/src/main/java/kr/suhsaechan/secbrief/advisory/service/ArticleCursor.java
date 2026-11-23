package kr.suhsaechan.secbrief.advisory.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;
import java.util.Base64;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 목록 위치 커서: (게시일, id) <-> URL-safe Base64 문자열 (원문 "{epochMilli}:{id}", 패딩 없음)
 */
public record ArticleCursor(Instant publishedAt, long id) {

    private static final String SEPARATOR = ":";

    public static String encode(Instant publishedAt, long id) {
        String rawCursor = publishedAt.toEpochMilli() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rawCursor.getBytes(UTF_8));
    }

    /**
     * 깨진 커서는 INVALID_CURSOR
     */
    public static ArticleCursor decode(String cursor) {
        try {
            String rawCursor = new String(Base64.getUrlDecoder().decode(cursor), UTF_8);
            String[] parts = rawCursor.split(SEPARATOR, -1);
            if (parts.length != 2) {
                throw new CustomException(ErrorCode.INVALID_CURSOR);
            }
            return new ArticleCursor(Instant.ofEpochMilli(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException exception) {
            // Base64 오류·숫자 오류 (NumberFormatException 포함)
            throw new CustomException(ErrorCode.INVALID_CURSOR);
        }
    }
}
