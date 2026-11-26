package kr.suhsaechan.secbrief.member.dto;

import java.util.List;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;

/**
 * 저장된 관심 기술: 제품·분야·생태계 code (글자순)
 */
public record InterestsResponse(List<String> productCodes, List<String> fieldCodes, List<String> ecosystemCodes) {

    public static InterestsResponse from(AlertInterests alertInterests) {
        return new InterestsResponse(alertInterests.productCodes().stream().sorted().toList(),
                alertInterests.fieldCodes().stream().sorted().toList(),
                alertInterests.ecosystemCodes().stream().sorted().toList());
    }
}
