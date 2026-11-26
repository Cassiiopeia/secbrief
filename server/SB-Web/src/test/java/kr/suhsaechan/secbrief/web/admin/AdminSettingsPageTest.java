package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLog;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLogRepository;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigCache;
import kr.suhsaechan.secbrief.common.config.SystemConfigHistory;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 설정 화면 (DB): 묶음별 목록 렌더링, 폼 POST 로 저장·기본값 복원(CSRF 필수) + 이력·감사 기록, 검증 거부는 에러 코드와 함께 안내
 * 비밀값·전용 화면 설정은 여기서 바꿀 수 없고 값이 화면에 내려가지 않음
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSettingsPageTest extends PostgresContainerTestSupport {

    private static final String SETTINGS_PATH = "/admin/settings";
    private static final String SECRET_VALUE = "[{\"id\":\"a1b2c3d4\",\"nickname\":\"운영\",\"apiKey\":\"sk-test-settings-page-000000-WXYZ\",\"isEnabled\":true}]";
    private static final int RECENT_AUDIT_LOG_COUNT = 20;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SystemConfigService systemConfigService;
    @Autowired
    private SystemConfigCache systemConfigCache;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String adminUsername = AdminTestAccounts.randomUsername();

    @BeforeEach
    @AfterEach
    void clearStoredConfigs() {
        jdbcTemplate.update("delete from system_config");
        jdbcTemplate.update("delete from system_config_history");
        systemConfigCache.invalidate();
    }

    @Test
    void requiresAdminLogin() throws Exception {
        mockMvc.perform(get(SETTINGS_PATH)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
        mockMvc.perform(post(SETTINGS_PATH + "/AI_TITLE_BATCH_SIZE").with(csrf()).param("value", "50"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
        assertThat(systemConfigService.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
    }

    @Test
    void rendersEverySettingGroupedWithInputsByValueType() throws Exception {
        getAsAdmin(SETTINGS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>설정 · 시큐브리프 관리자</title>")))
                .andExpect(content().string(containsString("AI 제공자 · 키")))
                .andExpect(content().string(containsString("AI 처리량")))
                .andExpect(content().string(containsString("AI 비용")))
                .andExpect(content().string(containsString("제목 분석 한 틱 건수")))
                .andExpect(content().string(containsString("action=\"/admin/settings/AI_TITLE_BATCH_SIZE\"")))
                .andExpect(content().string(containsString("action=\"/admin/settings/URGENT_ALERT_ENABLED\"")))
                .andExpect(content().string(containsString("href=\"/admin/settings/SUMMARY_DAILY_LIMIT/history\"")))
                .andExpect(content().string(containsString("type=\"number\"")))
                .andExpect(content().string(containsString("class=\"toggle")))
                .andExpect(content().string(containsString("name=\"reason\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("운영 설정 값")))
                // 전용 화면 설정은 폼 없이 이동 버튼만
                .andExpect(content().string(not(containsString("action=\"/admin/settings/ANTHROPIC_API_KEYS\""))))
                .andExpect(content().string(not(containsString("action=\"/admin/settings/AI_PROVIDER_PRIORITY\""))))
                .andExpect(content().string(containsString("AI 키 화면에서 바꾸기")))
                // 저장한 값이 없으면 기본값 복원 버튼이 없음
                .andExpect(content().string(not(containsString("/reset\""))));
    }

    @Test
    void neverSendsSecretPlainTextOrCipherTextToThePage() throws Exception {
        systemConfigService.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, adminUsername, null);

        getAsAdmin(SETTINGS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("설정됨")))
                .andExpect(content().string(containsString("••••")))
                .andExpect(content().string(not(containsString("sk-test-settings-page"))))
                .andExpect(content().string(not(containsString("WXYZ"))))
                .andExpect(content().string(not(containsString("enc:v1:"))));
        getAsAdmin(SETTINGS_PATH + "/ANTHROPIC_API_KEYS/history").andExpect(status().isOk())
                .andExpect(content().string(containsString("있음")))
                .andExpect(content().string(not(containsString("sk-test-settings-page"))))
                .andExpect(content().string(not(containsString("enc:v1:"))));
    }

    @Test
    void rejectsPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post(SETTINGS_PATH + "/AI_TITLE_BATCH_SIZE").with(user(adminUsername).roles("ADMIN"))
                        .param("value", "50"))
                .andExpect(status().isForbidden());

        assertThat(systemConfigService.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
    }

    @Test
    void savesValueWithReasonAndRecordsHistoryAndAudit() throws Exception {
        postAsAdmin(post(SETTINGS_PATH + "/AI_TITLE_BATCH_SIZE").param("value", " 50 ").param("reason", "새 소식이 밀려서"))
                .andExpect(redirectedUrl(SETTINGS_PATH))
                .andExpect(flash().attribute("flashMessage", containsString("저장했어요")));

        assertThat(systemConfigService.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(50);
        SystemConfigHistory history = systemConfigService.findHistories(ConfigKey.AI_TITLE_BATCH_SIZE).get(0);
        assertThat(history.getOldValue()).isEqualTo("20");
        assertThat(history.getNewValue()).isEqualTo("50");
        assertThat(history.getChangedBy()).isEqualTo(adminUsername);
        assertThat(history.getReason()).isEqualTo("새 소식이 밀려서");
        AdminAuditLog auditLog = findAuditLogs().get(0);
        assertThat(auditLog.getAction()).isEqualTo(AdminAuditAction.CONFIG_UPDATE);
        assertThat(auditLog.getTargetId()).isEqualTo("AI_TITLE_BATCH_SIZE");
        assertThat(auditLog.getDetail()).isEqualTo("20 -> 50");

        // 저장한 값이 화면에 보이고 기본값 복원·이력이 열림
        getAsAdmin(SETTINGS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"50\"")))
                .andExpect(content().string(containsString("저장한 값")))
                .andExpect(content().string(containsString("action=\"/admin/settings/AI_TITLE_BATCH_SIZE/reset\"")));
        getAsAdmin(SETTINGS_PATH + "/AI_TITLE_BATCH_SIZE/history").andExpect(status().isOk())
                .andExpect(content().string(containsString("제목 분석 한 틱 건수 변경 이력")))
                .andExpect(content().string(containsString("새 소식이 밀려서")))
                .andExpect(content().string(containsString(adminUsername)));
    }

    @Test
    void doesNotRecordAuditWhenValueIsUnchanged() throws Exception {
        postAsAdmin(post(SETTINGS_PATH + "/AI_TITLE_BATCH_SIZE").param("value", "20"))
                .andExpect(flash().attribute("flashMessage", containsString("그대로예요")));

        assertThat(findAuditLogs()).isEmpty();
        assertThat(systemConfigService.findHistories(ConfigKey.AI_TITLE_BATCH_SIZE)).isEmpty();
    }

    @Test
    void uncheckedToggleSavesFalseAndCheckedToggleSavesTrue() throws Exception {
        // 꺼진 토글은 value 가 오지 않음
        postAsAdmin(post(SETTINGS_PATH + "/URGENT_ALERT_ENABLED"));
        assertThat(systemConfigService.getBoolean(ConfigKey.URGENT_ALERT_ENABLED)).isFalse();

        postAsAdmin(post(SETTINGS_PATH + "/URGENT_ALERT_ENABLED").param("value", "true"));
        assertThat(systemConfigService.getBoolean(ConfigKey.URGENT_ALERT_ENABLED)).isTrue();
    }

    @Test
    void showsErrorCodeWhenValueIsRejected() throws Exception {
        postAsAdmin(post(SETTINGS_PATH + "/AI_TITLE_BATCH_SIZE").param("value", "0"))
                .andExpect(redirectedUrl(SETTINGS_PATH))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_VALUE_OUT_OF_RANGE")));
        postAsAdmin(post(SETTINGS_PATH + "/AI_DAILY_BUDGET_USD").param("value", "많이"))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_VALUE_NOT_NUMBER")));
        postAsAdmin(post(SETTINGS_PATH + "/SUMMARY_BATCH_SIZE").param("value", " "))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_VALUE_REQUIRED")));
        postAsAdmin(post(SETTINGS_PATH + "/NO_SUCH_KEY").param("value", "1"))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_KEY_NOT_FOUND")));

        assertThat(jdbcTemplate.queryForObject("select count(*) from system_config", Long.class)).isZero();
        assertThat(findAuditLogs()).isEmpty();
    }

    @Test
    void refusesToEditDedicatedScreenSettingsHere() throws Exception {
        postAsAdmin(post(SETTINGS_PATH + "/ANTHROPIC_API_KEYS").param("value", SECRET_VALUE))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_NOT_EDITABLE_HERE")));
        postAsAdmin(post(SETTINGS_PATH + "/AI_PROVIDER_PRIORITY").param("value", "ollama"))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_NOT_EDITABLE_HERE")));
        postAsAdmin(post(SETTINGS_PATH + "/ANTHROPIC_API_KEYS/reset"))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_NOT_EDITABLE_HERE")));

        assertThat(jdbcTemplate.queryForObject("select count(*) from system_config", Long.class)).isZero();
    }

    @Test
    void restoresDefaultAndRecordsAudit() throws Exception {
        postAsAdmin(post(SETTINGS_PATH + "/SUMMARY_DAILY_LIMIT").param("value", "120"));

        postAsAdmin(post(SETTINGS_PATH + "/SUMMARY_DAILY_LIMIT/reset"))
                .andExpect(redirectedUrl(SETTINGS_PATH))
                .andExpect(flash().attribute("flashMessage", containsString("기본값으로 돌렸어요")));

        assertThat(systemConfigService.getInt(ConfigKey.SUMMARY_DAILY_LIMIT)).isEqualTo(300);
        AdminAuditLog auditLog = findAuditLogs().get(0);
        assertThat(auditLog.getAction()).isEqualTo(AdminAuditAction.CONFIG_RESET);
        assertThat(auditLog.getDetail()).isEqualTo("120 -> 300");
        // 이미 기본값이면 안내만
        postAsAdmin(post(SETTINGS_PATH + "/SUMMARY_DAILY_LIMIT/reset"))
                .andExpect(flash().attribute("flashMessage", containsString("이미 기본값이에요")));
    }

    @Test
    void unknownKeyHistoryGoesBackToSettingsWithErrorCode() throws Exception {
        getAsAdmin(SETTINGS_PATH + "/NO_SUCH_KEY/history")
                .andExpect(redirectedUrl(SETTINGS_PATH))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_KEY_NOT_FOUND")));
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(adminUsername).roles("ADMIN")));
    }

    private ResultActions postAsAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user(adminUsername).roles("ADMIN")).with(csrf()));
    }

    // 이 테스트의 관리자가 남긴 감사 기록 (최신순)
    private List<AdminAuditLog> findAuditLogs() {
        return adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, RECENT_AUDIT_LOG_COUNT)).getContent().stream()
                .filter(auditLog -> adminUsername.equals(auditLog.getAdminUsername()))
                .toList();
    }
}
