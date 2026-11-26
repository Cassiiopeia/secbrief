package kr.suhsaechan.secbrief.advisory.dto;

import kr.suhsaechan.secbrief.advisory.summary.AffectedTarget;

/**
 * 브리핑의 영향 대상 응답 1개: 대상 이름(원문 글자 그대로) + 버전 범위·조건(원문 숫자 그대로, 없으면 null)
 */
public record AffectedTargetResponse(String target, String detail) {

    public static AffectedTargetResponse from(AffectedTarget affectedTarget) {
        return new AffectedTargetResponse(affectedTarget.target(), affectedTarget.detail());
    }
}
