package kr.suhsaechan.secbrief.application.admin.source;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.entity.CollectRun;
import kr.suhsaechan.secbrief.application.admin.label.AdminDisplayLabels;

/**
 * 출처의 가장 최근 실패 기록: 에러 코드, 정리된 문구, 실패 시각
 */
public record AdminSourceFailure(long sourceId, String errorCode, String errorMessage, Instant failedAt) {

    /**
     * 화면용 사유: 중단 기록은 한국어 이름, 그 밖은 CODE: 문구 (없는 쪽은 뺌)
     */
    public String toReason() {
        if (CollectRun.INTERRUPTED_ERROR_CODE.equals(errorCode)) {
            return AdminDisplayLabels.COLLECT_INTERRUPTED_REASON;
        }
        boolean hasCode = errorCode != null && !errorCode.isBlank();
        boolean hasMessage = errorMessage != null && !errorMessage.isBlank();
        if (hasCode && hasMessage) {
            return errorCode + ": " + errorMessage;
        }
        return hasCode ? errorCode : (hasMessage ? errorMessage : null);
    }
}
