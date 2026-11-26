package kr.suhsaechan.secbrief.advisory.constant;

/**
 * 경보단계 변경 방향 3개: RAISED 상향, LOWERED 하향·환원, KEPT 같은 단계 재발령
 * 바뀌기 전·후 단계 중 하나라도 모르면 방향 없음 (null)
 */
public enum AlertLevelChangeDirection {
    RAISED,
    LOWERED,
    KEPT;

    /** 바뀌기 전 > 후 단계 비교 (둘 중 하나라도 null 이면 null) */
    public static AlertLevelChangeDirection of(CyberCrisisLevel fromLevel, CyberCrisisLevel toLevel) {
        if (fromLevel == null || toLevel == null) {
            return null;
        }
        int stepDifference = toLevel.getStep() - fromLevel.getStep();
        if (stepDifference > 0) {
            return RAISED;
        }
        return stepDifference < 0 ? LOWERED : KEPT;
    }
}
