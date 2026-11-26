package kr.suhsaechan.secbrief.member.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import kr.suhsaechan.secbrief.member.entity.AlertInterests;

/**
 * 관심 기술 요청: 제품·분야·생태계 code 목록 (빈 목록 허용, 목록마다 최대 100개)
 * 카탈로그에 없는 code 는 저장 단계에서 빠짐 (지운 항목)
 */
public record InterestsRequest(
        @Size(max = MAXIMUM_CODE_COUNT, message = "관심 제품이 너무 많아요.")
        Set<@Pattern(regexp = CODE_PATTERN, message = "관심 기술 값을 확인해 주세요.") String> productCodes,

        @Size(max = MAXIMUM_CODE_COUNT, message = "관심 분야가 너무 많아요.")
        Set<@Pattern(regexp = CODE_PATTERN, message = "관심 기술 값을 확인해 주세요.") String> fieldCodes,

        @Size(max = MAXIMUM_CODE_COUNT, message = "관심 생태계가 너무 많아요.")
        Set<@Pattern(regexp = CODE_PATTERN, message = "관심 기술 값을 확인해 주세요.") String> ecosystemCodes
) {

    // 목록마다 최대 개수
    public static final int MAXIMUM_CODE_COUNT = 100;
    // catalog_tag.code 모양과 같음 (CatalogTag.CODE_PATTERN)
    public static final String CODE_PATTERN = "^[a-z0-9][a-z0-9-]{0,63}$";

    public AlertInterests toAlertInterests() {
        return new AlertInterests(productCodes, fieldCodes, ecosystemCodes);
    }
}
