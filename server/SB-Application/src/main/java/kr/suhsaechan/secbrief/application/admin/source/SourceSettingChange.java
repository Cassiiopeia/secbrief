package kr.suhsaechan.secbrief.application.admin.source;

/**
 * 출처 수집 설정 변경 결과: 바꾸기 전·후 값 (감사 기록·안내 문구용)
 */
public record SourceSettingChange(
        String sourceCode,
        String displayName,
        boolean isPreviouslyEnabled,
        boolean isEnabled,
        int previousIntervalMinutes,
        int intervalMinutes
) {

    public boolean isChanged() {
        return isPreviouslyEnabled != isEnabled || previousIntervalMinutes != intervalMinutes;
    }

    /**
     * 감사 기록 상세: enabled true -> false, intervalMinutes 30 -> 60 (바뀐 항목만)
     */
    public String toAuditDetail() {
        StringBuilder detail = new StringBuilder();
        if (isPreviouslyEnabled != isEnabled) {
            detail.append("enabled ").append(isPreviouslyEnabled).append(" -> ").append(isEnabled);
        }
        if (previousIntervalMinutes != intervalMinutes) {
            if (!detail.isEmpty()) {
                detail.append(", ");
            }
            detail.append("intervalMinutes ").append(previousIntervalMinutes).append(" -> ").append(intervalMinutes);
        }
        return detail.toString();
    }
}
