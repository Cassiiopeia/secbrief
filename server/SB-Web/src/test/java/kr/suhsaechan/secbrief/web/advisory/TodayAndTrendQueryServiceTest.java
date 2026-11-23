package kr.suhsaechan.secbrief.web.advisory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.advisory.dto.TodayResponse;
import kr.suhsaechan.secbrief.advisory.dto.TrendTopicResponse;
import kr.suhsaechan.secbrief.advisory.dto.WeeklyTrendResponse;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.service.TodayQueryService;
import kr.suhsaechan.secbrief.advisory.service.WeeklyTrendQueryService;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * 오늘·동향 (DB, 고정 시계): 서울 자정 기준 건수, 긴급 72시간, 긴급 제외 목록, 동향 1위 general 제외, 같은 경과 구간 비교
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ArticleTestWriter.class, TodayAndTrendQueryServiceTest.FixedClockConfig.class})
class TodayAndTrendQueryServiceTest extends PostgresContainerTestSupport {

    // 테스트마다 바꾸는 현재 시각
    private static final AtomicReference<Instant> NOW = new AtomicReference<>();

    @Autowired
    private TodayQueryService todayQueryService;
    @Autowired
    private WeeklyTrendQueryService weeklyTrendQueryService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleTestWriter articleTestWriter;

    @BeforeEach
    void clearArticles() {
        articleRepository.deleteAll();
        NOW.set(Instant.parse("2026-11-23T16:30:00Z"));
    }

    @Test
    void todayCountsUseSeoulMidnight() {
        // now = 11-24 01:30 KST > 오늘 시작 = 11-23 15:00Z
        articleTestWriter.saveNews(Instant.parse("2026-11-23T15:10:00Z"), ArticleTopic.GENERAL);
        articleTestWriter.saveNews(Instant.parse("2026-11-23T14:50:00Z"), ArticleTopic.GENERAL);

        TodayResponse today = todayQueryService.findToday();

        assertThat(today.todayCount()).isEqualTo(1);
        assertThat(today.date()).hasToString("2026-11-24");
    }

    @Test
    void urgentItemExpiresAfterSeventyTwoHours() {
        Article oldUrgent = articleTestWriter.saveArticle(NOW.get().minus(Duration.ofHours(73)),
                ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, ArticleTopic.VULNERABILITY, true);

        TodayResponse today = todayQueryService.findToday();

        assertThat(today.urgentItem()).isNull();
        assertThat(today.streamItems()).extracting(ArticleResponse::id).contains(oldUrgent.getId());
    }

    @Test
    void streamExcludesUrgentItem() {
        Article urgent = articleTestWriter.saveArticle(NOW.get().minus(Duration.ofHours(1)),
                ArticleKind.VULNERABILITY, ArticleRegion.OVERSEAS, ArticleTopic.VULNERABILITY, true);
        articleTestWriter.saveNews(NOW.get().minus(Duration.ofHours(2)), ArticleTopic.GENERAL);
        articleTestWriter.saveNews(NOW.get().minus(Duration.ofHours(3)), ArticleTopic.GENERAL);

        TodayResponse today = todayQueryService.findToday();

        assertThat(today.urgentItem().id()).isEqualTo(urgent.getId());
        assertThat(today.streamItems()).hasSize(2).extracting(ArticleResponse::id).doesNotContain(urgent.getId());
    }

    @Test
    void topTrendSkipsGeneral() {
        Instant thisWeek = NOW.get().minus(Duration.ofHours(1));
        for (int index = 0; index < 5; index++) {
            articleTestWriter.saveNews(thisWeek, ArticleTopic.GENERAL);
        }
        articleTestWriter.saveNews(thisWeek, ArticleTopic.RANSOMWARE);
        articleTestWriter.saveNews(thisWeek, ArticleTopic.RANSOMWARE);

        assertThat(todayQueryService.findToday().topTrend().topic()).isEqualTo(ArticleTopic.RANSOMWARE);
    }

    @Test
    void weeklyTrendComparesSameElapsedSpanOfPreviousWeek() {
        // now 수요일 12:00 KST (03:00Z), 이번 주 시작 = 월요일 00:00 KST (11-22 15:00Z)
        NOW.set(Instant.parse("2026-11-25T03:00:00Z"));
        Instant mondayStart = Instant.parse("2026-11-22T15:00:00Z");
        for (int hours : new int[]{1, 20, 40}) {
            articleTestWriter.saveNews(mondayStart.plus(Duration.ofHours(hours)), ArticleTopic.RANSOMWARE);
        }
        Instant previousMondayStart = mondayStart.minus(Duration.ofDays(7));
        articleTestWriter.saveNews(previousMondayStart.plus(Duration.ofHours(5)), ArticleTopic.RANSOMWARE);
        articleTestWriter.saveNews(previousMondayStart.plus(Duration.ofHours(30)), ArticleTopic.RANSOMWARE);
        // 지난주 목요일 (같은 경과 구간 밖)
        articleTestWriter.saveNews(previousMondayStart.plus(Duration.ofDays(3)), ArticleTopic.RANSOMWARE);

        WeeklyTrendResponse trend = weeklyTrendQueryService.findWeeklyTrend();

        TrendTopicResponse ransomware = trend.topics().get(0);
        assertThat(trend.weekStartDate()).hasToString("2026-11-23");
        assertThat(ransomware.topic()).isEqualTo(ArticleTopic.RANSOMWARE);
        assertThat(ransomware.weeklyCount()).isEqualTo(3);
        assertThat(ransomware.previousWeekCount()).isEqualTo(2);
        assertThat(ransomware.changePercent()).isEqualTo(50);
    }

    @Test
    void weeklyTrendListsAllEightTopics() {
        WeeklyTrendResponse trend = weeklyTrendQueryService.findWeeklyTrend();

        assertThat(trend.topics()).hasSize(8).allSatisfy(topic -> {
            assertThat(topic.weeklyCount()).isZero();
            assertThat(topic.changePercent()).isNull();
        });
    }

    @TestConfiguration
    static class FixedClockConfig {

        // NOW 를 읽는 시계 (테스트마다 시각 변경)
        @Bean
        @Primary
        Clock testClock() {
            return new Clock() {
                @Override
                public java.time.ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return NOW.get();
                }
            };
        }
    }
}
