package kr.suhsaechan.secbrief.advisory.collector;

import java.util.List;

/**
 * NVD 응답 1페이지: CVSS 9.0 이상 항목 + 다음 페이지 판정용 위치 값
 */
public record NvdPage(
        List<CollectedArticle> articles,
        int startIndex,
        int resultsPerPage,
        int totalResults
) {

    /**
     * 다음 페이지 여부: startIndex + resultsPerPage < totalResults
     */
    public boolean hasNextPage() {
        return resultsPerPage > 0 && startIndex + resultsPerPage < totalResults;
    }
}
