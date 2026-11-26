package kr.suhsaechan.secbrief.advisory.dto;

import java.time.Instant;
import java.util.List;

/**
 * 오늘 새로 들어온 소식 건수 (기기 날짜 0시 이후 수집한 공개 소식): 전체, 악용 중, KISA, 뉴스, 분야별·생태계별(뉴스 제외, 0건 뺌), 출처별
 */
public record NewArticleCountsResponse(Instant since, Instant lastCollectedAt, int totalCount, int exploitedCount,
                                       int kisaCount, int newsCount, List<TagCount> fieldCounts,
                                       List<TagCount> ecosystemCounts, List<SourceCount> sourceCounts) {

    /** 분류 태그 1개 건수 + 대표 이름 앞 3개 (원문 표기) */
    public record TagCount(String code, int count, List<String> sampleNames) {
    }

    public record SourceCount(String sourceCode, int count) {
    }
}
