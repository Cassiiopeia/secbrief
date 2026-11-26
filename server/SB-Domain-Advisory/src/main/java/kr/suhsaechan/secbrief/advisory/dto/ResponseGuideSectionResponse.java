package kr.suhsaechan.secbrief.advisory.dto;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ResponseGuideSection;

/**
 * 대응 모아보기 구역 1개: 구역 + 찾은 전체 건수(후보 상한 안에서) + 보여 줄 항목(최대 5건, 묶인 이유 순 > KEV·CVSS·최신순)
 */
public record ResponseGuideSectionResponse(
        ResponseGuideSection section,
        int totalCount,
        List<ResponseGuideItemResponse> items
) {
}
