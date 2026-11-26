package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageDailyRepository;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageFlushService;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageGroup;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageKey;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 이용 집계 (DB): 날짜·묶음 행에 더하기(upsert), 공개 API 요청 > 메모리 카운터 > 저장, 실패 응답·집계 밖 경로는 세지 않음
 * 저장하는 열은 날짜·묶음·건수뿐 (개인 식별값 없음)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiUsageRecordingTest extends PostgresContainerTestSupport {

    private static final LocalDate PAST_DATE = LocalDate.of(2019, 12, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApiUsageDailyRepository apiUsageDailyRepository;
    @Autowired
    private ApiUsageFlushService apiUsageFlushService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private Clock clock;

    @Test
    void addsToExistingRowInsteadOfReplacing() {
        jdbcTemplate.update("delete from api_usage_daily where usage_date = ?", Date.valueOf(PAST_DATE));
        ApiUsageKey usageKey = new ApiUsageKey(PAST_DATE, ApiUsageGroup.TODAY);

        apiUsageDailyRepository.addRequestCount(usageKey, 3);
        apiUsageDailyRepository.addRequestCount(usageKey, 4);
        apiUsageDailyRepository.addRequestCount(new ApiUsageKey(PAST_DATE, ApiUsageGroup.SEARCH), 1);

        assertThat(requestCountOf(PAST_DATE, ApiUsageGroup.TODAY)).isEqualTo(7);
        assertThat(requestCountOf(PAST_DATE, ApiUsageGroup.SEARCH)).isEqualTo(1);
    }

    @Test
    void countsHandledPublicApiRequestsAndSavesOnFlush() throws Exception {
        LocalDate today = SeoulCalendar.toSeoulDate(clock.instant());
        apiUsageFlushService.flush();
        long previousSourceCount = requestCountOf(today, ApiUsageGroup.SOURCE);
        long previousDetailCount = requestCountOf(today, ApiUsageGroup.ARTICLE_DETAIL);

        mockMvc.perform(get("/api/v1/sources")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sources")).andExpect(status().isOk());
        // 없는 기사(404)와 상태 확인은 세지 않음
        mockMvc.perform(get("/api/v1/articles/999999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        apiUsageFlushService.flush();

        assertThat(requestCountOf(today, ApiUsageGroup.SOURCE)).isEqualTo(previousSourceCount + 2);
        assertThat(requestCountOf(today, ApiUsageGroup.ARTICLE_DETAIL)).isEqualTo(previousDetailCount);
    }

    @Test
    void storesOnlyDateGroupAndCount() {
        List<String> columnNames = jdbcTemplate.queryForList("""
                select column_name from information_schema.columns
                where table_name = 'api_usage_daily' order by ordinal_position""", String.class);

        assertThat(columnNames).containsExactly("usage_date", "usage_group", "request_count");
    }

    private long requestCountOf(LocalDate usageDate, ApiUsageGroup usageGroup) {
        List<Long> requestCounts = jdbcTemplate.queryForList(
                "select request_count from api_usage_daily where usage_date = ? and usage_group = ?",
                Long.class, Date.valueOf(usageDate), usageGroup.name());
        return requestCounts.isEmpty() ? 0 : requestCounts.get(0);
    }
}
