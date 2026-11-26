package kr.suhsaechan.secbrief.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 관리자 화면 렌더링 (DB): 로그인, 대시보드, 출처, 관리자 기록(쪽 번호), 비밀번호 변경이 틀·CSRF·메뉴와 함께 열림
 * 추이 API 4종이 기본 기간(일별 30칸, 월별 12칸)으로 JSON 을 돌려줌
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminPageRenderTest extends PostgresContainerTestSupport {

    private static final String ADMIN_USERNAME = AdminTestAccounts.randomUsername();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminAuditService adminAuditService;

    @Test
    void rendersLoginPageWithGuides() throws Exception {
        mockMvc.perform(get("/admin/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("시큐브리프 관리자")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("ADMIN_LOGIN_LOCKED"))));
        mockMvc.perform(get("/admin/login").param("locked", "")).andExpect(status().isOk())
                .andExpect(content().string(containsString("ADMIN_LOGIN_LOCKED")));
        mockMvc.perform(get("/admin/login").param("error", "")).andExpect(status().isOk())
                .andExpect(content().string(containsString("ADMIN_LOGIN_FAILED")));
        mockMvc.perform(get("/admin/login").param("logout", "")).andExpect(status().isOk())
                .andExpect(content().string(containsString("로그아웃했어요")));
    }

    @Test
    void rendersDashboardWithLayoutAndCharts() throws Exception {
        getAsAdmin("/admin").andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>대시보드 · 시큐브리프 관리자</title>")))
                .andExpect(content().string(containsString("meta name=\"_csrf\"")))
                .andExpect(content().string(containsString("/admin/vendor/daisyui-5.css")))
                .andExpect(content().string(containsString("/admin/js/admin-ui.js")))
                .andExpect(content().string(containsString(ADMIN_USERNAME)))
                .andExpect(content().string(containsString("오늘 수집")))
                .andExpect(content().string(containsString("data-endpoint=\"/admin/stats/api/collect\"")))
                .andExpect(content().string(containsString("data-endpoint=\"/admin/stats/api/ai-usage\"")))
                .andExpect(content().string(containsString("data-endpoint=\"/admin/stats/api/api-usage\"")))
                .andExpect(content().string(containsString("data-endpoint=\"/admin/stats/api/push-devices\"")))
                .andExpect(content().string(containsString("KISA 보호나라")))
                // 사이드바 메뉴 7개 + 지금 화면 강조
                .andExpect(content().string(containsString("href=\"/admin/sources\"")))
                .andExpect(content().string(containsString("href=\"/admin/settings\"")))
                .andExpect(content().string(containsString("href=\"/admin/ai-keys\"")))
                .andExpect(content().string(containsString("href=\"/admin/ai-calls\"")))
                .andExpect(content().string(containsString("href=\"/admin/audit\"")))
                .andExpect(content().string(containsString("href=\"/admin/password\"")))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    @Test
    void rendersSourcesWithUpdateForms() throws Exception {
        getAsAdmin("/admin/sources").andExpect(status().isOk())
                .andExpect(content().string(containsString("출처 관리")))
                .andExpect(content().string(containsString("BLEEPING_COMPUTER")))
                .andExpect(content().string(containsString("name=\"intervalMinutes\"")))
                .andExpect(content().string(containsString("data-confirm-message")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void rendersAuditLogsNewestFirstWithPagination() throws Exception {
        adminAuditService.record(ADMIN_USERNAME, AdminAuditAction.SOURCE_UPDATE, "RENDER_TEST_SOURCE", "render test detail");

        getAsAdmin("/admin/audit").andExpect(status().isOk())
                .andExpect(content().string(containsString("RENDER_TEST_SOURCE")))
                .andExpect(content().string(containsString("출처 설정 변경")))
                .andExpect(content().string(containsString("render test detail")))
                .andExpect(content().string(containsString("전체")));
        // 숫자가 아닌 쪽 번호·범위를 넘는 쪽도 화면은 열림
        getAsAdmin("/admin/audit?page=abc").andExpect(status().isOk());
        getAsAdmin("/admin/audit?page=99999").andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 남은 기록이 없어요")));
    }

    @Test
    void rendersPasswordForm() throws Exception {
        getAsAdmin("/admin/password").andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"currentPassword\"")))
                .andExpect(content().string(containsString("name=\"newPasswordConfirm\"")))
                .andExpect(content().string(containsString("minlength=\"10\"")));
    }

    @Test
    void statsApisReturnTrendChartsForDefaultPeriods() throws Exception {
        getAsAdmin("/admin/stats/api/collect").andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.unit").value("day"))
                .andExpect(jsonPath("$.labels", hasSize(30)))
                .andExpect(jsonPath("$.bucketStarts", hasSize(30)))
                .andExpect(jsonPath("$.series", hasSize(3)))
                .andExpect(jsonPath("$.series[0].key").value("NEWS"))
                .andExpect(jsonPath("$.series[0].values", hasSize(30)));
        getAsAdmin("/admin/stats/api/collect?unit=month").andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("month"))
                .andExpect(jsonPath("$.labels", hasSize(12)));
        getAsAdmin("/admin/stats/api/ai-usage").andExpect(status().isOk())
                .andExpect(jsonPath("$.series", hasSize(2)));
        getAsAdmin("/admin/stats/api/ai-usage?metric=cost").andExpect(status().isOk())
                .andExpect(jsonPath("$.series", hasSize(1)))
                .andExpect(jsonPath("$.series[0].key").value("AI_COST"));
        getAsAdmin("/admin/stats/api/api-usage").andExpect(status().isOk())
                .andExpect(jsonPath("$.series", hasSize(8)));
        getAsAdmin("/admin/stats/api/push-devices").andExpect(status().isOk())
                .andExpect(jsonPath("$.series", hasSize(1)));
        // 틀린 기간 값은 기본값으로, 366일을 넘는 기간은 366칸으로
        getAsAdmin("/admin/stats/api/collect?from=2000-01-01&to=bad").andExpect(status().isOk())
                .andExpect(jsonPath("$.labels", hasSize(366)));
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(ADMIN_USERNAME).roles("ADMIN")));
    }
}
