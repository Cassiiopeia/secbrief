package kr.suhsaechan.secbrief.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallAttempt;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallType;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiTokenUsage;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * AI 호출 화면 (DB): 합계 카드(오늘·이번 달), 최근 호출 표, 용도·결과 필터, 필터를 들고 가는 쪽 번호 (읽기 전용)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiCallPageTest extends PostgresContainerTestSupport {

    private static final String AI_CALLS_PATH = "/admin/ai-calls";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AiCallLogService aiCallLogService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String adminUsername = AdminTestAccounts.randomUsername();

    @BeforeEach
    @AfterEach
    void clearCallLogs() {
        jdbcTemplate.update("delete from ai_call_log");
    }

    @Test
    void requiresAdminLogin() throws Exception {
        mockMvc.perform(get(AI_CALLS_PATH)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void rendersEmptyStateWithZeroSummaries() throws Exception {
        getAsAdmin(AI_CALLS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>AI 호출 · 시큐브리프 관리자</title>")))
                .andExpect(content().string(containsString("오늘 호출")))
                .andExpect(content().string(containsString("이번 달 추정 비용")))
                .andExpect(content().string(containsString("$0.0000")))
                .andExpect(content().string(containsString("아직 남은 호출 기록이 없어요")))
                .andExpect(content().string(containsString("name=\"callType\"")))
                .andExpect(content().string(containsString("name=\"result\"")));
    }

    @Test
    void rendersCallRowsAndSummaries() throws Exception {
        aiCallLogService.recordSuccess(
                new AiCallAttempt(AiCallType.TITLE_ANALYSIS, "anthropic", "claude-haiku-5-5", "운영", 1_842),
                new AiTokenUsage(12_000, 3_000));
        aiCallLogService.recordFailure(
                new AiCallAttempt(AiCallType.SUMMARY, "anthropic", "claude-haiku-5-5", "예비", 310),
                AiFailureKind.RATE_LIMITED);
        aiCallLogService.recordSuccess(
                new AiCallAttempt(AiCallType.SUMMARY, "ollama", "test-model", null, 5_120), new AiTokenUsage(812, 96));

        getAsAdmin(AI_CALLS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("제목 분석")))
                .andExpect(content().string(containsString("claude-haiku-5-5")))
                .andExpect(content().string(containsString("운영")))
                .andExpect(content().string(containsString("예비")))
                .andExpect(content().string(containsString("한도 초과 (429)")))
                .andExpect(content().string(containsString("1,842ms")))
                .andExpect(content().string(containsString("12,000")))
                .andExpect(content().string(containsString("$0.002700")))
                // 합계: 호출 3 (성공 2 · 실패 1), 추정 비용 0.0027
                .andExpect(content().string(containsString("성공 2 · 실패 1")))
                .andExpect(content().string(containsString("$0.0027")))
                .andExpect(content().string(containsString("입력 12,812 · 출력 3,096 토큰")));
    }

    @Test
    void filtersByCallTypeAndResult() throws Exception {
        aiCallLogService.recordSuccess(
                new AiCallAttempt(AiCallType.TITLE_ANALYSIS, "anthropic", "claude-haiku-5-5", "제목용키", 100),
                new AiTokenUsage(10, 5));
        aiCallLogService.recordFailure(
                new AiCallAttempt(AiCallType.SUMMARY, "anthropic", "claude-haiku-5-5", "요약용키", 100),
                AiFailureKind.UNAUTHORIZED);

        getAsAdmin(AI_CALLS_PATH + "?callType=SUMMARY").andExpect(status().isOk())
                .andExpect(content().string(containsString("요약용키")))
                .andExpect(content().string(not(containsString("제목용키"))));
        getAsAdmin(AI_CALLS_PATH + "?result=success").andExpect(status().isOk())
                .andExpect(content().string(containsString("제목용키")))
                .andExpect(content().string(not(containsString("요약용키"))));
        getAsAdmin(AI_CALLS_PATH + "?callType=TITLE_ANALYSIS&result=failure").andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 남은 호출 기록이 없어요")));
        // 모르는 필터 값·숫자가 아닌 쪽 번호도 화면은 열림 (전체로 봄)
        getAsAdmin(AI_CALLS_PATH + "?callType=NO_SUCH&result=maybe&page=abc").andExpect(status().isOk())
                .andExpect(content().string(containsString("제목용키")))
                .andExpect(content().string(containsString("요약용키")));
    }

    @Test
    void paginationLinksKeepFilters() throws Exception {
        // 한 쪽 50건: 51건이면 2쪽
        jdbcTemplate.update("insert into ai_call_log (call_type, provider, model, key_label, is_success, latency_ms, created_at) "
                + "select 'SUMMARY', 'anthropic', 'claude-haiku-5-5', '쪽번호-' || series, true, 100, now() "
                + "from generate_series(1, 51) as series");

        getAsAdmin(AI_CALLS_PATH + "?callType=SUMMARY&result=success").andExpect(status().isOk())
                .andExpect(content().string(containsString("전체 51건")))
                .andExpect(content().string(containsString("쪽번호-51")))
                .andExpect(content().string(not(containsString("쪽번호-1<"))))
                .andExpect(content().string(containsString("href=\"/admin/ai-calls?callType=SUMMARY&amp;result=success&amp;page=2\"")));
        getAsAdmin(AI_CALLS_PATH + "?callType=SUMMARY&result=success&page=2").andExpect(status().isOk())
                .andExpect(content().string(containsString("쪽번호-1<")));
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(adminUsername).roles("ADMIN")));
    }
}
