package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.application.admin.stats.AdminAiUsageMetric;
import kr.suhsaechan.secbrief.application.admin.stats.AdminTrendChart;
import kr.suhsaechan.secbrief.application.admin.stats.AdminTrendRange;
import kr.suhsaechan.secbrief.application.admin.stats.AdminTrendSeries;
import kr.suhsaechan.secbrief.application.admin.stats.AdminTrendService;
import kr.suhsaechan.secbrief.application.admin.stats.AdminTrendUnit;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 추이 집계 (DB): 서울 날짜 경계로 칸 나누기, 빈 날 0 채움, 월별 묶음, 줄 순서 고정
 * 수집(종류별), AI 호출(성공·실패·비용), 이용(화면 묶음, 이미지 제외), 알림 기기 등록
 * 다른 테스트 자료와 섞이지 않게 2019년 날짜만 씀 (결과 캐시 때문에 테스트마다 기간이 다름)
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ArticleTestWriter.class)
class AdminTrendQueryTest extends PostgresContainerTestSupport {

    @Autowired
    private AdminTrendService adminTrendService;
    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private ArticleTestWriter articleTestWriter;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTrendRows() {
        articleRepository.deleteAll();
        jdbcTemplate.update("delete from ai_call_log where created_at < '2020-01-01'");
        jdbcTemplate.update("delete from api_usage_daily where usage_date < '2020-01-01'");
        jdbcTemplate.update("delete from push_device where created_at < '2020-01-01'");
    }

    @Test
    void collectTrendSplitsDaysAtSeoulMidnightAndFillsEmptyDays() {
        // 서울 3/11 00:30, 3/11 23:59, 3/12 00:00
        saveArticle("2019-03-10T15:30:00Z", ArticleKind.NEWS);
        saveArticle("2019-03-11T14:59:00Z", ArticleKind.ADVISORY);
        saveArticle("2019-03-11T15:00:00Z", ArticleKind.NEWS);

        AdminTrendChart chart = adminTrendService.loadCollectTrend(dayRange("2019-03-10", "2019-03-13"));

        assertThat(chart.unit()).isEqualTo("day");
        assertThat(chart.labels()).containsExactly("3/10", "3/11", "3/12", "3/13");
        assertThat(chart.bucketStarts()).containsExactly("2019-03-10", "2019-03-11", "2019-03-12", "2019-03-13");
        assertThat(chart.series()).extracting(AdminTrendSeries::key).containsExactly("NEWS", "ADVISORY", "VULNERABILITY");
        assertThat(chart.series()).extracting(AdminTrendSeries::label).containsExactly("뉴스", "공지", "취약점");
        assertThat(valuesOf(chart, "NEWS")).containsExactly(0L, 1L, 1L, 0L);
        assertThat(valuesOf(chart, "ADVISORY")).containsExactly(0L, 1L, 0L, 0L);
        assertThat(valuesOf(chart, "VULNERABILITY")).containsExactly(0L, 0L, 0L, 0L);
    }

    @Test
    void collectTrendGroupsByMonthFromFirstDayOfStartMonth() {
        // 서울 1/5, 2/1 00:00 (UTC 로는 1/31), 3/20
        saveArticle("2019-01-05T03:00:00Z", ArticleKind.VULNERABILITY);
        saveArticle("2019-01-31T15:00:00Z", ArticleKind.VULNERABILITY);
        saveArticle("2019-03-20T03:00:00Z", ArticleKind.VULNERABILITY);

        // 시작 날짜가 달 중간이어도 그달 1일부터 셈
        AdminTrendChart chart = adminTrendService.loadCollectTrend(
                new AdminTrendRange(AdminTrendUnit.MONTH, LocalDate.parse("2019-01-15"), LocalDate.parse("2019-04-10")));

        assertThat(chart.unit()).isEqualTo("month");
        assertThat(chart.labels()).containsExactly("2019-01", "2019-02", "2019-03", "2019-04");
        assertThat(valuesOf(chart, "VULNERABILITY")).containsExactly(1L, 1L, 1L, 0L);
    }

    @Test
    void emptyPeriodReturnsAllSeriesWithZeros() {
        AdminTrendChart chart = adminTrendService.loadCollectTrend(dayRange("2019-06-01", "2019-06-03"));

        assertThat(chart.series()).hasSize(3);
        assertThat(chart.series()).allSatisfy(series -> assertThat(series.values()).containsExactly(0L, 0L, 0L));
    }

    @Test
    void aiUsageTrendCountsSuccessAndFailure() {
        saveAiCall("2019-04-01T01:00:00Z", true, "0.001200");
        saveAiCall("2019-04-01T02:00:00Z", true, "0.000300");
        saveAiCall("2019-04-01T03:00:00Z", false, null);
        // 서울 4/3 00:10
        saveAiCall("2019-04-02T15:10:00Z", false, null);

        AdminTrendChart chart = adminTrendService.loadAiUsageTrend(dayRange("2019-04-01", "2019-04-03"),
                AdminAiUsageMetric.CALL_COUNT);

        assertThat(chart.series()).extracting(AdminTrendSeries::key).containsExactly("AI_SUCCESS", "AI_FAILURE");
        assertThat(valuesOf(chart, "AI_SUCCESS")).containsExactly(2L, 0L, 0L);
        assertThat(valuesOf(chart, "AI_FAILURE")).containsExactly(1L, 0L, 1L);
    }

    @Test
    void aiUsageTrendSumsEstimatedCost() {
        saveAiCall("2019-05-01T01:00:00Z", true, "0.001200");
        saveAiCall("2019-05-01T02:00:00Z", true, "0.000300");

        AdminTrendChart chart = adminTrendService.loadAiUsageTrend(dayRange("2019-05-01", "2019-05-02"),
                AdminAiUsageMetric.COST_USD);

        assertThat(chart.series()).extracting(AdminTrendSeries::key).containsExactly("AI_COST");
        assertThat(valuesOf(chart, "AI_COST")).containsExactly(0.0015, 0.0);
    }

    @Test
    void apiUsageTrendShowsScreenGroupsWithoutImageRequests() {
        saveApiUsage("2019-07-01", "TODAY", 5);
        saveApiUsage("2019-07-02", "TODAY", 7);
        saveApiUsage("2019-07-02", "SEARCH", 2);
        saveApiUsage("2019-07-02", "ARTICLE_IMAGE", 100);

        AdminTrendChart chart = adminTrendService.loadApiUsageTrend(dayRange("2019-07-01", "2019-07-02"));

        assertThat(chart.series()).extracting(AdminTrendSeries::key).containsExactly("TODAY", "ARTICLE_LIST",
                "ARTICLE_DETAIL", "SEARCH", "KEYWORD", "TREND", "SOURCE", "PUSH_DEVICE", "NOTIFICATION");
        assertThat(valuesOf(chart, "TODAY")).containsExactly(5L, 7L);
        assertThat(valuesOf(chart, "SEARCH")).containsExactly(0L, 2L);
    }

    @Test
    void apiUsageTrendSumsDaysIntoMonths() {
        saveApiUsage("2019-08-30", "TODAY", 5);
        saveApiUsage("2019-08-31", "TODAY", 7);
        saveApiUsage("2019-09-01", "TODAY", 1);

        AdminTrendChart chart = adminTrendService.loadApiUsageTrend(
                new AdminTrendRange(AdminTrendUnit.MONTH, LocalDate.parse("2019-08-01"), LocalDate.parse("2019-09-30")));

        assertThat(chart.labels()).containsExactly("2019-08", "2019-09");
        assertThat(valuesOf(chart, "TODAY")).containsExactly(12L, 1L);
    }

    @Test
    void pushDeviceTrendCountsRegistrationsByDay() {
        savePushDevice(LocalDateTime.parse("2019-10-01T00:00:00"));
        savePushDevice(LocalDateTime.parse("2019-10-01T23:59:59"));
        savePushDevice(LocalDateTime.parse("2019-10-03T12:00:00"));

        AdminTrendChart chart = adminTrendService.loadPushDeviceTrend(dayRange("2019-10-01", "2019-10-03"));

        assertThat(chart.series()).hasSize(1);
        assertThat(chart.series().get(0).values()).containsExactly(2L, 0L, 1L);
    }

    private static AdminTrendRange dayRange(String from, String to) {
        return new AdminTrendRange(AdminTrendUnit.DAY, LocalDate.parse(from), LocalDate.parse(to));
    }

    private static List<Number> valuesOf(AdminTrendChart chart, String seriesKey) {
        return chart.series().stream().filter(series -> series.key().equals(seriesKey)).findFirst().orElseThrow().values();
    }

    private void saveArticle(String collectedAt, ArticleKind kind) {
        articleTestWriter.saveArticle(Instant.parse(collectedAt), kind, ArticleRegion.DOMESTIC, ArticleTopic.GENERAL, false);
    }

    private void saveAiCall(String createdAt, boolean isSuccess, String estimatedCostUsd) {
        jdbcTemplate.update("""
                insert into ai_call_log (call_type, provider, model, key_label, is_success, estimated_cost_usd, created_at)
                values ('TITLE_ANALYSIS', 'anthropic', 'test-model', 'config', ?, ?, ?)""",
                isSuccess, estimatedCostUsd == null ? null : new BigDecimal(estimatedCostUsd),
                Timestamp.from(Instant.parse(createdAt)));
    }

    private void saveApiUsage(String usageDate, String usageGroup, long requestCount) {
        jdbcTemplate.update("insert into api_usage_daily (usage_date, usage_group, request_count) values (?, ?, ?)",
                Date.valueOf(usageDate), usageGroup, requestCount);
    }

    private void savePushDevice(LocalDateTime createdAt) {
        jdbcTemplate.update("insert into push_device (push_token, platform, created_at, updated_at) values (?, 'ANDROID', ?, ?)",
                "ExponentPushToken[trend-" + UUID.randomUUID() + "]", Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
    }
}
