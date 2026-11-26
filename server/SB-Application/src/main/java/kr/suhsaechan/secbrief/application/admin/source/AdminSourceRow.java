package kr.suhsaechan.secbrief.application.admin.source;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;

/**
 * 출처 관리 표 1행: 설정(코드, 이름, 종류, 주기, 켜짐) + 마지막 수집(성공 시각, 상태) + 최근 실패(사유, 시각)
 */
public record AdminSourceRow(
        long id,
        String code,
        String displayName,
        String kindLabel,
        int intervalMinutes,
        boolean isEnabled,
        Instant lastCollectedAt,
        CollectStatus lastStatus,
        String lastStatusLabel,
        // 가장 최근 실패 기록 (없으면 null): 에러 코드 + 정리된 문구
        String lastFailureReason,
        Instant lastFailedAt
) {

    public boolean isLastCollectFailed() {
        return lastStatus == CollectStatus.FAILED;
    }
}
