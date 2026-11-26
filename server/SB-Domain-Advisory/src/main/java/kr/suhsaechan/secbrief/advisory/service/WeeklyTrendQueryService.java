package kr.suhsaechan.secbrief.advisory.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.TrendTopicResponse;
import kr.suhsaechan.secbrief.advisory.dto.WeeklyTrendResponse;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.repository.TopicCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주간 동향: 이번 주 [월요일 00시, 지금) <-> 지난주 같은 경과 구간, 주제 9종 (0건 포함)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyTrendQueryService {

    private static final Duration ONE_WEEK = Duration.ofDays(7);

    private final ArticleRepository articleRepository;
    private final Clock clock;

    public WeeklyTrendResponse findWeeklyTrend() {
        Instant now = clock.instant();
        Instant startOfWeek = SeoulCalendar.startOfWeek(now);
        Map<ArticleTopic, Long> weeklyCountByTopic = toCountByTopic(articleRepository.countByTopicBetween(startOfWeek, now));
        Map<ArticleTopic, Long> previousWeekCountByTopic = toCountByTopic(
                articleRepository.countByTopicBetween(startOfWeek.minus(ONE_WEEK), now.minus(ONE_WEEK)));

        // 이번 주 건수 내림차순, 같으면 enum 순서
        List<TrendTopicResponse> topics = Arrays.stream(ArticleTopic.values())
                .map(topic -> toTrendTopic(topic, weeklyCountByTopic.getOrDefault(topic, 0L),
                        previousWeekCountByTopic.getOrDefault(topic, 0L)))
                .sorted(Comparator.comparingLong(TrendTopicResponse::weeklyCount).reversed()
                        .thenComparing(trend -> trend.topic().ordinal()))
                .toList();
        return new WeeklyTrendResponse(SeoulCalendar.toSeoulDate(startOfWeek), topics);
    }

    /**
     * 증감률 (%): 반올림, 지난주 0 이면 null
     */
    public static Integer toChangePercent(long weeklyCount, long previousWeekCount) {
        if (previousWeekCount == 0) {
            return null;
        }
        return (int) Math.round((weeklyCount - previousWeekCount) * 100.0 / previousWeekCount);
    }

    private static TrendTopicResponse toTrendTopic(ArticleTopic topic, long weeklyCount, long previousWeekCount) {
        return new TrendTopicResponse(topic, weeklyCount, previousWeekCount, toChangePercent(weeklyCount, previousWeekCount));
    }

    private static Map<ArticleTopic, Long> toCountByTopic(List<TopicCount> topicCounts) {
        return topicCounts.stream().collect(Collectors.toMap(TopicCount::topic, TopicCount::count));
    }
}
