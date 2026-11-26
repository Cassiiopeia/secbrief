package kr.suhsaechan.secbrief.advisory.summary;

/**
 * 브리핑의 영향 대상 1개: 대상 이름(입력에 적힌 그대로), 버전 범위·조건(입력에 적힌 숫자 그대로, 없으면 null)
 */
public record AffectedTarget(String target, String detail) {

    // 이전 앱 빌드의 핵심 정리 한 줄: "대상: 조건"
    private static final String LEGACY_DETAIL_SEPARATOR = ": ";

    /** 이전 구조의 핵심 정리 한 줄 (조건이 없으면 대상 이름만) */
    public String toLegacyKeyPoint() {
        return detail == null ? target : target + LEGACY_DETAIL_SEPARATOR + detail;
    }
}
