package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLogRepository;
import kr.suhsaechan.secbrief.member.admin.AdminAccountProperties;
import kr.suhsaechan.secbrief.member.admin.AdminRepository;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginAttemptLimiter;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비밀번호 변경 화면 (DB): 바꾸면 새 비밀번호로만 로그인 + 감사 기록(비밀번호 값 없음),
 * 현재 비밀번호 불일치·확인 값 불일치·규칙 위반은 에러 코드와 함께 거부하고 값 유지
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminTestAccounts.class)
class AdminPasswordChangeTest extends PostgresContainerTestSupport {

    private static final String PASSWORD_PATH = "/admin/password";
    private static final String LOGIN_PATH = "/admin/login";
    private static final int RECENT_AUDIT_LOG_COUNT = 10;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminTestAccounts adminTestAccounts;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired
    private AdminLoginAttemptLimiter adminLoginAttemptLimiter;

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
    void changesPasswordAndRecordsAuditWithoutPasswordValue() throws Exception {
        String newPassword = AdminTestAccounts.randomPassword();

        postPasswordChange(account.password(), newPassword, newPassword)
                .andExpect(redirectedUrl(PASSWORD_PATH))
                .andExpect(flash().attribute("flashMessage", containsString("바꿨어요")));

        mockMvc.perform(formLogin(LOGIN_PATH).user(account.username()).password(newPassword)).andExpect(authenticated());
        mockMvc.perform(formLogin(LOGIN_PATH).user(account.username()).password(account.password()))
                .andExpect(unauthenticated());
        assertThat(adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, RECENT_AUDIT_LOG_COUNT)).getContent())
                .filteredOn(auditLog -> auditLog.getAction() == AdminAuditAction.PASSWORD_CHANGE
                        && account.username().equals(auditLog.getAdminUsername()))
                .singleElement()
                .satisfies(auditLog -> assertThat(auditLog.getDetail()).isNull());
    }

    @Test
    void rejectsWrongCurrentPassword() throws Exception {
        String newPassword = AdminTestAccounts.randomPassword();

        postPasswordChange(AdminTestAccounts.randomPassword(), newPassword, newPassword)
                .andExpect(flash().attribute("flashError", containsString("ADMIN_CURRENT_PASSWORD_MISMATCH")));

        assertPasswordUnchanged();
    }

    @Test
    void rejectsConfirmMismatch() throws Exception {
        postPasswordChange(account.password(), AdminTestAccounts.randomPassword(), AdminTestAccounts.randomPassword())
                .andExpect(flash().attribute("flashError", containsString("ADMIN_PASSWORD_CONFIRM_MISMATCH")));

        assertPasswordUnchanged();
    }

    @Test
    void rejectsPasswordAgainstPolicy() throws Exception {
        for (String weakPassword : new String[] {"short1", "onlyletterspassword", "123456789012"}) {
            postPasswordChange(account.password(), weakPassword, weakPassword)
                    .andExpect(flash().attribute("flashError", containsString("ADMIN_PASSWORD_POLICY_VIOLATION")));
        }

        assertPasswordUnchanged();
    }

    private ResultActions postPasswordChange(String currentPassword, String newPassword, String newPasswordConfirm)
            throws Exception {
        return mockMvc.perform(post(PASSWORD_PATH).with(user(account.username()).roles("ADMIN")).with(csrf())
                .param("currentPassword", currentPassword)
                .param("newPassword", newPassword)
                .param("newPasswordConfirm", newPasswordConfirm));
    }

    private void assertPasswordUnchanged() throws Exception {
        assertThat(adminRepository.findByUsername(account.username())).isPresent();
        mockMvc.perform(formLogin(LOGIN_PATH).user(account.username()).password(account.password()))
                .andExpect(authenticated());
    }
}
