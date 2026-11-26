package kr.suhsaechan.secbrief.application.admin.source;

import java.time.Instant;

/**
 * 출처의 가장 최근 실패 기록: 에러 코드, 정리된 문구, 실패 시각
 */
public record AdminSourceFailure(long sourceId, String errorCode, String errorMessage, Instant failedAt) {

    /**
     * 화면용 사유: CODE: 문구 (없는 쪽은 뺌)
     */
    public String toReason() {
        boolean hasCode = errorCode != null && !errorCode.isBlank();
        boolean hasMessage = errorMessage != null && !errorMessage.isBlank();
        if (hasCode && hasMessage) {
            return errorCode + ": " + errorMessage;
        }
        return hasCode ? errorCode : (hasMessage ? errorMessage : null);
    }
}
