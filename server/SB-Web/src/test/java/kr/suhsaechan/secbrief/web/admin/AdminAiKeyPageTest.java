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
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKey;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKeyService;
import kr.suhsaechan.secbrief.advisory.ai.key.ApiKeyHealthTracker;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLog;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLogRepository;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigCache;
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
 * AI 키 화면 (DB): 키 표 렌더링(가린 값·상태), 폼 POST 로 추가·이동·켜기·끄기·값 교체·별명 변경·삭제(CSRF 필수), 제공자 우선순위
 * 키 값은 DB 에 암호문으로만 남고 화면·안내 문구·이력·감사 기록 어디에도 나오지 않음
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiKeyPageTest extends PostgresContainerTestSupport {

    private static final String AI_KEYS_PATH = "/admin/ai-keys";
    private static final String FIRST_API_KEY = "sk-test-page-first-00000000000-AAAA";
    private static final String SECOND_API_KEY = "sk-test-page-second-0000000000-BBBB";
    private static final String REISSUED_API_KEY = "sk-test-page-reissued-00000000-CCCC";
    private static final int RECENT_AUDIT_LOG_COUNT = 30;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AnthropicApiKeyService anthropicApiKeyService;
    @Autowired
    private ApiKeyHealthTracker apiKeyHealthTracker;
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
    void requiresAdminLoginAndCsrfToken() throws Exception {
        mockMvc.perform(get(AI_KEYS_PATH)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
        mockMvc.perform(post(AI_KEYS_PATH).with(user(adminUsername).roles("ADMIN"))
                        .param("nickname", "운영").param("apiKey", FIRST_API_KEY))
                .andExpect(status().isForbidden());

        assertThat(anthropicApiKeyService.findStoredKeys()).isEmpty();
    }

    @Test
    void rendersEmptyKeyTableWithAddFormAndProviderPriority() throws Exception {
        getAsAdmin(AI_KEYS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>AI 키 · 시큐브리프 관리자</title>")))
                .andExpect(content().string(containsString("등록한 키가 없어요")))
                .andExpect(content().string(containsString("0 / 10개")))
                .andExpect(content().string(containsString("action=\"/admin/ai-keys\"")))
                .andExpect(content().string(containsString("name=\"nickname\"")))
                .andExpect(content().string(containsString("type=\"password\" name=\"apiKey\"")))
                .andExpect(content().string(containsString("maxlength=\"30\"")))
                .andExpect(content().string(containsString("action=\"/admin/ai-keys/provider-priority\"")))
                .andExpect(content().string(containsString("name=\"providerCodes\"")))
                .andExpect(content().string(containsString("anthropic")))
                .andExpect(content().string(containsString("ollama")))
                .andExpect(content().string(containsString("키·주소 없음")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                // 테스트 설정에는 마스터 키가 있어 안내 배너가 없음
                .andExpect(content().string(not(containsString("SECRET_MASTER_KEY_MISSING"))));
    }

    @Test
    void addsKeyEncryptedAndShowsOnlyMaskedValue() throws Exception {
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", "운영").param("apiKey", FIRST_API_KEY))
                .andExpect(redirectedUrl(AI_KEYS_PATH))
                .andExpect(flash().attribute("flashMessage", containsString("운영 키를 추가했어요")))
                .andExpect(flash().attribute("flashMessage", not(containsString("sk-test"))));

        // DB 에는 암호문만
        String storedValue = jdbcTemplate.queryForObject(
                "select config_value from system_config where config_key = 'ANTHROPIC_API_KEYS'", String.class);
        assertThat(storedValue).startsWith("enc:v1:").doesNotContain("sk-test");
        assertThat(anthropicApiKeyService.findCallableKeys()).extracting(AnthropicApiKey::apiKey).containsExactly(FIRST_API_KEY);
        // 화면에는 별명과 가린 값만
        String keyId = findKeyId("운영");
        getAsAdmin(AI_KEYS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("운영")))
                .andExpect(content().string(containsString("••••AAAA")))
                .andExpect(content().string(containsString("1 / 10개")))
                .andExpect(content().string(containsString("쓰는 중")))
                .andExpect(content().string(containsString("action=\"/admin/ai-keys/" + keyId + "/remove\"")))
                .andExpect(content().string(containsString("action=\"/admin/ai-keys/" + keyId + "/replace\"")))
                .andExpect(content().string(containsString("data-confirm-message")))
                .andExpect(content().string(not(containsString("sk-test-page"))))
                .andExpect(content().string(not(containsString("enc:v1:"))));
        // 이력·감사 기록에도 별명만
        AdminAuditLog auditLog = findAuditLogs().get(0);
        assertThat(auditLog.getAction()).isEqualTo(AdminAuditAction.AI_KEY_ADD);
        assertThat(auditLog.getTargetId()).isEqualTo(keyId);
        assertThat(auditLog.getDetail()).isEqualTo("운영");
        assertThat(jdbcTemplate.queryForList(
                "select coalesce(old_value, '') || coalesce(new_value, '') || coalesce(reason, '') from system_config_history",
                String.class)).containsExactly("없음있음AI 키 추가: 운영");
    }

    @Test
    void showsErrorCodeWithoutEchoingKeyValueWhenInputIsRejected() throws Exception {
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", "운영").param("apiKey", "sk-test with spaces 0000000000"))
                .andExpect(redirectedUrl(AI_KEYS_PATH))
                .andExpect(flash().attribute("flashError", containsString("AI_KEY_INVALID_VALUE")))
                .andExpect(flash().attribute("flashError", not(containsString("sk-test"))));
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", " ").param("apiKey", FIRST_API_KEY))
                .andExpect(flash().attribute("flashError", containsString("AI_KEY_INVALID_NICKNAME")));
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", "운영").param("apiKey", FIRST_API_KEY));
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", "운영").param("apiKey", SECOND_API_KEY))
                .andExpect(flash().attribute("flashError", containsString("AI_KEY_DUPLICATED_NICKNAME")));
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", "예비").param("apiKey", FIRST_API_KEY))
                .andExpect(flash().attribute("flashError", containsString("AI_KEY_DUPLICATED")));
        postAsAdmin(post(AI_KEYS_PATH + "/nokey000/remove"))
                .andExpect(flash().attribute("flashError", containsString("AI_KEY_NOT_FOUND")));

        assertThat(anthropicApiKeyService.findStoredKeys()).hasSize(1);
    }

    @Test
    void movesTogglesReplacesRenamesAndRemovesKey() throws Exception {
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", "운영").param("apiKey", FIRST_API_KEY));
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", "예비").param("apiKey", SECOND_API_KEY));
        String secondKeyId = findKeyId("예비");

        postAsAdmin(post(AI_KEYS_PATH + "/" + secondKeyId + "/move").param("direction", "up"))
                .andExpect(flash().attribute("flashMessage", containsString("올렸어요")));
        assertThat(findNicknames()).containsExactly("예비", "운영");

        postAsAdmin(post(AI_KEYS_PATH + "/" + secondKeyId + "/toggle").param("enabled", "false"))
                .andExpect(flash().attribute("flashMessage", containsString("껐어요")));
        assertThat(anthropicApiKeyService.findCallableKeys()).extracting(AnthropicApiKey::nickname).containsExactly("운영");
        postAsAdmin(post(AI_KEYS_PATH + "/" + secondKeyId + "/toggle").param("enabled", "true"));

        postAsAdmin(post(AI_KEYS_PATH + "/" + secondKeyId + "/replace").param("apiKey", REISSUED_API_KEY))
                .andExpect(flash().attribute("flashMessage", containsString("키 값을 바꿨어요")))
                .andExpect(flash().attribute("flashMessage", not(containsString("sk-test"))));
        assertThat(anthropicApiKeyService.findCallableKeys()).extracting(AnthropicApiKey::apiKey)
                .containsExactly(REISSUED_API_KEY, FIRST_API_KEY);

        postAsAdmin(post(AI_KEYS_PATH + "/" + secondKeyId + "/rename").param("nickname", "재발급"))
                .andExpect(flash().attribute("flashMessage", containsString("재발급")));
        assertThat(findNicknames()).containsExactly("재발급", "운영");

        postAsAdmin(post(AI_KEYS_PATH + "/" + secondKeyId + "/remove"))
                .andExpect(flash().attribute("flashMessage", containsString("삭제했어요")));
        assertThat(findNicknames()).containsExactly("운영");

        // 감사 기록: 동작마다 1건, 키 값은 어디에도 없음
        List<AdminAuditLog> auditLogs = findAuditLogs();
        assertThat(auditLogs).extracting(AdminAuditLog::getAction).containsExactly(
                AdminAuditAction.AI_KEY_REMOVE, AdminAuditAction.AI_KEY_RENAME, AdminAuditAction.AI_KEY_REPLACE,
                AdminAuditAction.AI_KEY_TOGGLE, AdminAuditAction.AI_KEY_TOGGLE, AdminAuditAction.AI_KEY_MOVE,
                AdminAuditAction.AI_KEY_ADD, AdminAuditAction.AI_KEY_ADD);
        assertThat(auditLogs).allSatisfy(auditLog -> assertThat(auditLog.getDetail()).doesNotContain("sk-test"));
        assertThat(jdbcTemplate.queryForList(
                "select coalesce(old_value, '') || coalesce(new_value, '') || coalesce(reason, '') from system_config_history",
                String.class)).noneMatch(historyText -> historyText.contains("sk-test") || historyText.contains("enc:v1:"));
    }

    @Test
    void showsCooldownAndLastFailureKindOfKey() throws Exception {
        postAsAdmin(post(AI_KEYS_PATH).param("nickname", "운영").param("apiKey", FIRST_API_KEY));
        apiKeyHealthTracker.recordFailure(findKeyId("운영"), AiFailureKind.RATE_LIMITED);

        getAsAdmin(AI_KEYS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("쉬는 중 (5분)")))
                .andExpect(content().string(containsString("한도 초과 (429)")));
    }

    @Test
    void savesProviderPriorityAndRejectsUnknownProvider() throws Exception {
        postAsAdmin(post(AI_KEYS_PATH + "/provider-priority").param("providerCodes", "ollama", "anthropic"))
                .andExpect(redirectedUrl(AI_KEYS_PATH))
                .andExpect(flash().attribute("flashMessage", containsString("우선순위를 저장했어요")));

        assertThat(systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY)).isEqualTo("ollama,anthropic");
        AdminAuditLog auditLog = findAuditLogs().get(0);
        assertThat(auditLog.getAction()).isEqualTo(AdminAuditAction.CONFIG_UPDATE);
        assertThat(auditLog.getTargetId()).isEqualTo("AI_PROVIDER_PRIORITY");
        assertThat(auditLog.getDetail()).isEqualTo("anthropic -> ollama,anthropic");
        getAsAdmin(AI_KEYS_PATH).andExpect(content().string(containsString("1순위")))
                .andExpect(content().string(containsString("2순위")));

        // 2순위 "없음"(빈 값)은 버림, 같은 값이면 그대로
        postAsAdmin(post(AI_KEYS_PATH + "/provider-priority").param("providerCodes", "ollama", "", "anthropic"))
                .andExpect(flash().attribute("flashMessage", containsString("그대로예요")));
        postAsAdmin(post(AI_KEYS_PATH + "/provider-priority").param("providerCodes", "openai"))
                .andExpect(flash().attribute("flashError", containsString("AI_PROVIDER_UNKNOWN")));
        postAsAdmin(post(AI_KEYS_PATH + "/provider-priority"))
                .andExpect(flash().attribute("flashError", containsString("CONFIG_VALUE_REQUIRED")));
        assertThat(systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY)).isEqualTo("ollama,anthropic");
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(adminUsername).roles("ADMIN")));
    }

    private ResultActions postAsAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user(adminUsername).roles("ADMIN")).with(csrf()));
    }

    private List<String> findNicknames() {
        return anthropicApiKeyService.findStoredKeys().stream().map(AnthropicApiKey::nickname).toList();
    }

    private String findKeyId(String nickname) {
        return anthropicApiKeyService.findStoredKeys().stream()
                .filter(key -> key.nickname().equals(nickname))
                .findFirst().orElseThrow().id();
    }

    // 이 테스트의 관리자가 남긴 감사 기록 (최신순)
    private List<AdminAuditLog> findAuditLogs() {
        return adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, RECENT_AUDIT_LOG_COUNT)).getContent().stream()
                .filter(auditLog -> adminUsername.equals(auditLog.getAdminUsername()))
                .toList();
    }
}
