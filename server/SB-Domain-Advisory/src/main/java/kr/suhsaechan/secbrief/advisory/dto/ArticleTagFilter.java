package kr.suhsaechan.secbrief.advisory.dto;

import java.time.Instant;
import java.util.List;

/**
 * 기사 목록 분류 조건 (#93, 값이 없으면 조건 없음): 출처 여러 곳, 분야 1개, 생태계 1개, 주요 여부,
 * 내 기술(제품·분야·생태계 중 하나라도 맞음), 이 시각 이후 수집 (오늘)
 */
public record ArticleTagFilter(List<String> sourceCodes, String fieldCode, String ecosystemCode, Boolean isNotable,
                               List<String> interestProductCodes, List<String> interestFieldCodes,
                               List<String> interestEcosystemCodes, Instant collectedSince) {

    // 쉼표 목록 1개 최대 개수 (조회 조건이 지나치게 길어지지 않게)
    public static final int MAXIMUM_LIST_SIZE = 100;

    public ArticleTagFilter {
        sourceCodes = toBoundedList(sourceCodes);
        interestProductCodes = toBoundedList(interestProductCodes);
        interestFieldCodes = toBoundedList(interestFieldCodes);
        interestEcosystemCodes = toBoundedList(interestEcosystemCodes);
    }

    public static ArticleTagFilter none() {
        return new ArticleTagFilter(List.of(), null, null, null, List.of(), List.of(), List.of(), null);
    }

    public boolean hasInterests() {
        return !interestProductCodes.isEmpty() || !interestFieldCodes.isEmpty() || !interestEcosystemCodes.isEmpty();
    }

    // 빈 값·공백 버림, 최대 100개
    private static List<String> toBoundedList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::strip)
                .distinct().limit(MAXIMUM_LIST_SIZE).toList();
    }
}
