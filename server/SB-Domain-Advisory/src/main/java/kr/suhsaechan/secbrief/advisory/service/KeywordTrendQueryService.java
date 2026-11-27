package kr.suhsaechan.secbrief.advisory.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.dto.TrendingKeywordResponse;
import kr.suhsaechan.secbrief.advisory.dto.TrendingKeywordsResponse;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.KeywordCount;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.common.request.PageSizePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인기 키워드: 최근 N일 [지금 - N일, 지금) <-> 바로 앞 N일의 키워드별 기사 건수 > 건수 많은 순 순위·순위 변화
 * 대소문자만 다른 키워드는 하나로 합침 (표기는 가장 많이 쓰인 것)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KeywordTrendQueryService {

    public static final int DEFAULT_PERIOD_DAYS = 7;
    public static final int MAXIMUM_PERIOD_DAYS = 30;
    public static final int DEFAULT_RANK_SIZE = 20;
    public static final int MAXIMUM_RANK_SIZE = 50;

    // 순위 기준: 건수 많은 순, 같으면 키워드 글자순 (매번 같은 순위가 나오게)
    private static final Comparator<MergedKeywordCount> MOST_ARTICLES_FIRST =
            Comparator.comparingLong(MergedKeywordCount::articleCount).reversed()
                    .thenComparing(MergedKeywordCount::lowerCaseKeyword);

    private final ArticleRepository articleRepository;
    private final Clock clock;

    public TrendingKeywordsResponse findTrendingKeywords(Integer days, Integer size) {
        Duration period = Duration.ofDays(toPeriodDays(days));
        int rankSize = toRankSize(size);
        Instant now = clock.instant();
        Instant periodStart = now.minus(period);

        return new TrendingKeywordsResponse(toRankedKeywords(
                articleRepository.countByKeywordBetween(periodStart, now),
                articleRepository.countByKeywordBetween(periodStart.minus(period), periodStart),
                rankSize));
    }

    /**
     * 이번 기간·앞 기간 건수 > 순위 목록 (상위 rankSize 건): 대소문자 합치기 > 기간별 순위 > 순위 변화(앞 순위 - 이번 순위)
     */
    public static List<TrendingKeywordResponse> toRankedKeywords(List<KeywordCount> keywordCounts,
                                                                List<KeywordCount> previousKeywordCounts, int rankSize) {
        List<MergedKeywordCount> rankedCounts = toRankedCounts(keywordCounts);
        List<MergedKeywordCount> previousRankedCounts = toRankedCounts(previousKeywordCounts);
        // 앞 기간: 키워드(소문자) > 순위(1부터)·건수
        Map<String, Integer> previousRankByKeyword = new HashMap<>();
        Map<String, Long> previousCountByKeyword = new HashMap<>();
        for (int previousIndex = 0; previousIndex < previousRankedCounts.size(); previousIndex++) {
            MergedKeywordCount previousCount = previousRankedCounts.get(previousIndex);
            previousRankByKeyword.put(previousCount.lowerCaseKeyword(), previousIndex + 1);
            previousCountByKeyword.put(previousCount.lowerCaseKeyword(), previousCount.articleCount());
        }

        List<TrendingKeywordResponse> rankedKeywords = new ArrayList<>();
        for (int rankIndex = 0; rankIndex < Math.min(rankSize, rankedCounts.size()); rankIndex++) {
            MergedKeywordCount rankedCount = rankedCounts.get(rankIndex);
            int rank = rankIndex + 1;
            Integer previousRank = previousRankByKeyword.get(rankedCount.lowerCaseKeyword());
            rankedKeywords.add(new TrendingKeywordResponse(
                    rankedCount.displayKeyword(),
                    rankedCount.articleCount(),
                    previousCountByKeyword.getOrDefault(rankedCount.lowerCaseKeyword(), 0L),
                    rank,
                    previousRank == null ? null : previousRank - rank));
        }
        return rankedKeywords;
    }

    // 대소문자만 다른 표기 합치기 (건수 합산, 표기는 건수가 가장 많은 것·같으면 글자순 앞) > 건수 많은 순
    private static List<MergedKeywordCount> toRankedCounts(List<KeywordCount> keywordCounts) {
        Map<String, Long> articleCountByKeyword = new HashMap<>();
        Map<String, KeywordCount> mostUsedSpellingByKeyword = new HashMap<>();
        for (KeywordCount keywordCount : keywordCounts) {
            String lowerCaseKeyword = keywordCount.keyword().toLowerCase(Locale.ROOT);
            articleCountByKeyword.merge(lowerCaseKeyword, keywordCount.count(), Long::sum);
            mostUsedSpellingByKeyword.merge(lowerCaseKeyword, keywordCount, KeywordTrendQueryService::toMoreUsedSpelling);
        }
        return articleCountByKeyword.entrySet().stream()
                .map(entry -> new MergedKeywordCount(entry.getKey(),
                        mostUsedSpellingByKeyword.get(entry.getKey()).keyword(), entry.getValue()))
                .sorted(MOST_ARTICLES_FIRST)
                .toList();
    }

    private static KeywordCount toMoreUsedSpelling(KeywordCount first, KeywordCount second) {
        if (first.count() != second.count()) {
            return first.count() > second.count() ? first : second;
        }
        return first.keyword().compareTo(second.keyword()) <= 0 ? first : second;
    }

    // 기간 (일): null 이면 7, 1~30 밖이면 INVALID_REQUEST
    private static int toPeriodDays(Integer days) {
        if (days == null) {
            return DEFAULT_PERIOD_DAYS;
        }
        if (days < 1 || days > MAXIMUM_PERIOD_DAYS) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "days out of range");
        }
        return days;
    }

    // 순위 크기: null·1 미만이면 20, 50 넘으면 50 (PageSizePolicy)
    private static int toRankSize(Integer size) {
        return PageSizePolicy.toClampedPageSize(size, DEFAULT_RANK_SIZE, MAXIMUM_RANK_SIZE);
    }

    // 합친 키워드 1개: 비교용 소문자, 보여 줄 표기, 기사 건수
    private record MergedKeywordCount(String lowerCaseKeyword, String displayKeyword, long articleCount) {
    }
}
