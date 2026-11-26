package kr.suhsaechan.secbrief.advisory.service;

import static kr.suhsaechan.secbrief.advisory.service.KeywordTrendQueryService.toRankedKeywords;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.dto.TrendingKeywordResponse;
import kr.suhsaechan.secbrief.advisory.repository.KeywordCount;
import org.junit.jupiter.api.Test;

/**
 * 인기 키워드 순위 계산: 건수 많은 순(같으면 글자순), 대소문자 합치기, 앞 기간 건수·순위 변화(상승 양수, 새 키워드 null), 상위 N건
 */
class KeywordTrendRankingTest {

    @Test
    void ranksByArticleCountThenKeywordOrder() {
        List<TrendingKeywordResponse> rankedKeywords = toRankedKeywords(
                List.of(count("제로데이", 2), count("랜섬웨어", 5), count("VPN", 2)), List.of(), 20);

        assertThat(rankedKeywords).extracting(TrendingKeywordResponse::keyword).containsExactly("랜섬웨어", "VPN", "제로데이");
        assertThat(rankedKeywords).extracting(TrendingKeywordResponse::rank).containsExactly(1, 2, 3);
        assertThat(rankedKeywords).extracting(TrendingKeywordResponse::articleCount).containsExactly(5L, 2L, 2L);
    }

    @Test
    void mergesSpellingsThatDifferOnlyByCaseUsingMostUsedSpelling() {
        List<TrendingKeywordResponse> rankedKeywords = toRankedKeywords(
                List.of(count("linux", 1), count("Linux", 3), count("LINUX", 1), count("VPN", 4)),
                List.of(count("LINUX", 2)), 20);

        assertThat(rankedKeywords).containsExactly(
                new TrendingKeywordResponse("Linux", 5, 2, 1, 0),
                new TrendingKeywordResponse("VPN", 4, 0, 2, null));
    }

    @Test
    void rankChangeIsPreviousRankMinusCurrentRank() {
        // 앞 기간 순위: 랜섬웨어 1, 제로데이 2, 피싱 3 > 이번 기간: 피싱 1, 랜섬웨어 2, 제로데이 3
        List<TrendingKeywordResponse> rankedKeywords = toRankedKeywords(
                List.of(count("피싱", 9), count("랜섬웨어", 6), count("제로데이", 3)),
                List.of(count("랜섬웨어", 8), count("제로데이", 4), count("피싱", 1)), 20);

        assertThat(rankedKeywords).containsExactly(
                new TrendingKeywordResponse("피싱", 9, 1, 1, 2),
                new TrendingKeywordResponse("랜섬웨어", 6, 8, 2, -1),
                new TrendingKeywordResponse("제로데이", 3, 4, 3, -1));
    }

    @Test
    void newKeywordHasNullRankChangeAndZeroPreviousCount() {
        List<TrendingKeywordResponse> rankedKeywords = toRankedKeywords(
                List.of(count("공급망 공격", 2)), List.of(count("랜섬웨어", 8)), 20);

        assertThat(rankedKeywords).containsExactly(new TrendingKeywordResponse("공급망 공격", 2, 0, 1, null));
    }

    @Test
    void keepsOnlyTopRankSizeButRanksPreviousPeriodAmongAllKeywords() {
        // 앞 기간에서 '피싱'은 3위 (상위 2건 밖이어도 순위 변화 계산에 씀)
        List<TrendingKeywordResponse> rankedKeywords = toRankedKeywords(
                List.of(count("피싱", 9), count("랜섬웨어", 6), count("제로데이", 3)),
                List.of(count("랜섬웨어", 8), count("제로데이", 4), count("피싱", 1)), 2);

        assertThat(rankedKeywords).extracting(TrendingKeywordResponse::keyword).containsExactly("피싱", "랜섬웨어");
        assertThat(rankedKeywords.get(0).rankChange()).isEqualTo(2);
    }

    @Test
    void returnsEmptyWhenNoKeywordInPeriod() {
        assertThat(toRankedKeywords(List.of(), List.of(count("랜섬웨어", 8)), 20)).isEmpty();
    }

    private static KeywordCount count(String keyword, long count) {
        return new KeywordCount(keyword, count);
    }
}
