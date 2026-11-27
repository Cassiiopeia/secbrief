package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLog;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLogRepository;
import kr.suhsaechan.secbrief.member.admin.AdminAccountProperties;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginAttemptLimiter;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginUsernames;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 보안 체인 (DB): 공개 API 는 로그인·CSRF 없이 통과하고 세션을 만들지 않음, 관리자 경로는 로그인 필요,
 * 로그인 성공·실패·5회 잠금(잠긴 동안 맞는 비밀번호도 거부), CSRF 없는 관리자 POST 거부, 그 밖의 경로 거부
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminTestAccounts.class)
class AdminSecurityTest extends PostgresContainerTestSupport {

    private static final String LOGIN_PATH = "/admin/login";
    private static final int RECENT_AUDIT_LOG_COUNT = 20;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestAccounts adminTestAccounts;
    @Autowired
    private AdminLoginAttemptLimiter adminLoginAttemptLimiter;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;

    private AdminAccountProperties.Account account;

    @BeforeEach
    void createAdminAccount() {
        account = adminTestAccounts.create();
    }

    @AfterEach
    void deleteAdminAccount() {
        adminLoginAttemptLimiter.clear(account.username());
        adminTestAccounts.deleteCreated();
    }

    @Test
    void publicApiPassesWithoutLoginAndCreatesNoSession() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        MvcResult sourceListMvcResult = mockMvc.perform(get("/api/v1/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn();

        assertThat(sourceListMvcResult.getRequest().getSession(false)).isNull();
        // 보안 기본 헤더가 공개 API 응답에 붙지 않음 (보안 추가 전과 같은 헤더)
        assertThat(sourceListMvcResult.getResponse().getHeaderNames()).doesNotContain("Cache-Control", "X-Frame-Options", "Pragma");
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void publicApiPutPassesWithoutCsrfToken() throws Exception {
        // 켠 종류가 없는 요청: 기기를 만들지 않고 200 (CSRF 에 막히면 403)
        mockMvc.perform(put("/api/v1/push-devices").contentType(MediaType.APPLICATION_JSON).content("""
                        {"pushToken":"ExponentPushToken[security-test]","platform":"ANDROID","enabledAlertCategories":[]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabledAlertCategories").isEmpty());
    }

    @Test
    void adminPagesRedirectToLoginWithoutSession() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
        mockMvc.perform(get("/admin/stats/api/collect")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
        mockMvc.perform(get("/admin/settings")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void loginPageAndStaticFilesOpenWithoutLogin() throws Exception {
        mockMvc.perform(get(LOGIN_PATH)).andExpect(status().isOk());
        mockMvc.perform(get("/admin/js/admin-ui.js")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/css/admin.css")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/vendor/daisyui-5.css")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=2592000"));
    }

    @Test
    void otherPathsAreDenied() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
    }

    @Test
    void nonAdminRoleCannotOpenAdminPages() throws Exception {
        mockMvc.perform(get("/admin").with(user("visitor").roles("USER"))).andExpect(status().isForbidden());
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndRecordsAudit() throws Exception {
        mockMvc.perform(formLogin(LOGIN_PATH).user(account.username()).password(account.password()))
                .andExpect(authenticated().withUsername(account.username()).withRoles("ADMIN"))
                .andExpect(redirectedUrl("/admin"));

        assertThat(recentAuditLogsOf(AdminAuditAction.LOGIN_SUCCESS))
                .anyMatch(auditLog -> account.username().equals(auditLog.getAdminUsername()));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(formLogin(LOGIN_PATH).user(account.username()).password(AdminTestAccounts.randomPassword()))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/admin/login?error"));
    }

    @Test
    void locksAfterFiveFailuresAndRejectsCorrectPasswordWhileLocked() throws Exception {
        for (int attempt = 1; attempt < AdminLoginAttemptLimiter.MAXIMUM_FAILURE_COUNT; attempt++) {
            mockMvc.perform(formLogin(LOGIN_PATH).user(account.username()).password(AdminTestAccounts.randomPassword()))
                    .andExpect(redirectedUrl("/admin/login?error"));
        }
        mockMvc.perform(formLogin(LOGIN_PATH).user(account.username()).password(AdminTestAccounts.randomPassword()))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/admin/login?locked"));

        // 잠긴 동안은 맞는 비밀번호도 대조하지 않고 되돌림
        mockMvc.perform(formLogin(LOGIN_PATH).user(account.username()).password(account.password()))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/admin/login?locked"));

        assertThat(recentAuditLogsOf(AdminAuditAction.LOGIN_LOCKED))
                .anyMatch(auditLog -> account.username().equals(auditLog.getTargetId()));
    }

    @Test
    void locksUnregisteredUsernameWithoutRecordingTypedValue() throws Exception {
        String typedUsername = AdminTestAccounts.randomUsername();
        try {
            for (int attempt = 1; attempt <= AdminLoginAttemptLimiter.MAXIMUM_FAILURE_COUNT; attempt++) {
                mockMvc.perform(formLogin(LOGIN_PATH).user(typedUsername).password(AdminTestAccounts.randomPassword()))
                        .andExpect(unauthenticated());
            }
            assertThat(adminLoginAttemptLimiter.isLocked(typedUsername)).isTrue();
            List<AdminAuditLog> lockedLogs = recentAuditLogsOf(AdminAuditAction.LOGIN_LOCKED);
            assertThat(lockedLogs).noneMatch(auditLog -> typedUsername.equals(auditLog.getTargetId()));
            assertThat(lockedLogs).anyMatch(auditLog ->
                    AdminLoginUsernames.UNREGISTERED_USERNAME.equals(auditLog.getTargetId()));
        } finally {
            adminLoginAttemptLimiter.clear(typedUsername);
        }
    }

    @Test
    void adminPostWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/admin/password").with(user(account.username()).roles("ADMIN"))
                        .param("currentPassword", account.password())
                        .param("newPassword", "x").param("newPasswordConfirm", "x"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(LOGIN_PATH).param("username", account.username()).param("password", account.password()))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutRedirectsToLoginPage() throws Exception {
        mockMvc.perform(post("/admin/logout").with(user(account.username()).roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/login?logout"));
    }

    private List<AdminAuditLog> recentAuditLogsOf(AdminAuditAction action) {
        return adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, RECENT_AUDIT_LOG_COUNT)).getContent()
                .stream().filter(auditLog -> auditLog.getAction() == action).toList();
    }
}
