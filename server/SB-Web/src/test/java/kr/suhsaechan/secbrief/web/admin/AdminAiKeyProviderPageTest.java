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
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKey;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyService;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyServiceRegistry;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
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
 * AI 키 화면의 제공자별 구역 (DB): Anthropic·OpenAI·Google 구역과 모델 표시, 제공자별 키 저장 위치·주소 분리,
 * 모르는 제공자 코드 거부, 새 제공자를 넣은 우선순위 저장
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiKeyProviderPageTest extends PostgresContainerTestSupport {

    private static final String AI_KEYS_PATH = "/admin/ai-keys";
    private static final String ANTHROPIC_KEYS_PATH = AI_KEYS_PATH + "/anthropic";
    private static final String OPENAI_KEYS_PATH = AI_KEYS_PATH + "/openai";
    private static final String GOOGLE_KEYS_PATH = AI_KEYS_PATH + "/google";
    private static final String FIRST_API_KEY = "sk-test-page-first-00000000000-AAAA";
    private static final String SECOND_API_KEY = "sk-test-page-second-0000000000-BBBB";
    private static final String REISSUED_API_KEY = "sk-test-page-reissued-00000000-CCCC";
    private static final int RECENT_AUDIT_LOG_COUNT = 30;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AiApiKeyServiceRegistry aiApiKeyServiceRegistry;
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
    void rendersOneKeySectionPerProviderWithModelAndOwnFormPaths() throws Exception {
        getAsAdmin(AI_KEYS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"anthropic\"")))
                .andExpect(content().string(containsString("id=\"openai\"")))
                .andExpect(content().string(containsString("id=\"google\"")))
                .andExpect(content().string(containsString("OpenAI 키")))
                .andExpect(content().string(containsString("Google 키")))
                .andExpect(content().string(containsString("action=\"/admin/ai-keys/openai\"")))
                .andExpect(content().string(containsString("action=\"/admin/ai-keys/google\"")))
                // 모델: Anthropic 은 고정, OpenAI·Google 은 설정 저장소 기본값
                .andExpect(content().string(containsString("claude-haiku-5-5")))
                .andExpect(content().string(containsString("고정")))
                .andExpect(content().string(containsString("gpt-6-luna")))
                .andExpect(content().string(containsString("gemini-3.5-flash-lite")))
                .andExpect(content().string(containsString("설정에서 바꾸기")))
                // 우선순위 구역에 새 제공자도 나옴
                .andExpect(content().string(containsString("<option value=\"openai\"")))
                .andExpect(content().string(containsString("<option value=\"google\"")));
    }

    @Test
    void storesOpenAiAndGoogleKeysEncryptedUnderTheirOwnConfigKeys() throws Exception {
        postAsAdmin(post(OPENAI_KEYS_PATH).param("nickname", "오픈 운영").param("apiKey", FIRST_API_KEY))
                .andExpect(redirectedUrl(AI_KEYS_PATH + "#openai"))
                .andExpect(flash().attribute("flashMessage", containsString("OpenAI 오픈 운영 키를 추가했어요")))
                .andExpect(flash().attribute("flashMessage", not(containsString("sk-test"))));
        postAsAdmin(post(GOOGLE_KEYS_PATH).param("nickname", "구글 운영").param("apiKey", SECOND_API_KEY))
                .andExpect(redirectedUrl(AI_KEYS_PATH + "#google"))
                .andExpect(flash().attribute("flashMessage", containsString("Google 구글 운영 키를 추가했어요")));

        assertThat(jdbcTemplate.queryForList("select config_key from system_config order by config_key", String.class))
                .containsExactly("GOOGLE_API_KEYS", "OPENAI_API_KEYS");
        assertThat(jdbcTemplate.queryForList("select config_value from system_config", String.class))
                .allSatisfy(storedValue -> assertThat(storedValue).startsWith("enc:v1:").doesNotContain("sk-test"));
        assertThat(findKeyService(KeyedAiProvider.OPENAI).findCallableKeys()).extracting(AiApiKey::apiKey)
                .containsExactly(FIRST_API_KEY);
        assertThat(findKeyService(KeyedAiProvider.GOOGLE).findCallableKeys()).extracting(AiApiKey::apiKey)
                .containsExactly(SECOND_API_KEY);
        assertThat(findKeyService(KeyedAiProvider.ANTHROPIC).findStoredKeys()).isEmpty();

        String openAiKeyId = findKeyService(KeyedAiProvider.OPENAI).findStoredKeys().get(0).id();
        getAsAdmin(AI_KEYS_PATH).andExpect(status().isOk())
                .andExpect(content().string(containsString("오픈 운영")))
                .andExpect(content().string(containsString("구글 운영")))
                .andExpect(content().string(containsString("••••AAAA")))
                .andExpect(content().string(containsString("••••BBBB")))
                .andExpect(content().string(containsString("action=\"/admin/ai-keys/openai/" + openAiKeyId + "/toggle\"")))
                .andExpect(content().string(containsString("id=\"keyEditModal-openai-" + openAiKeyId + "\"")))
                .andExpect(content().string(not(containsString("sk-test-page"))));
        // 감사 기록: 제공자 이름 + 별명 (키 값 없음)
        assertThat(findAuditLogs()).extracting(AdminAuditLog::getDetail)
                .containsExactly("Google · 구글 운영", "OpenAI · 오픈 운영");
    }

    @Test
    void changesKeysOfOneProviderWithoutTouchingAnother() throws Exception {
        postAsAdmin(post(ANTHROPIC_KEYS_PATH).param("nickname", "운영").param("apiKey", FIRST_API_KEY));
        postAsAdmin(post(GOOGLE_KEYS_PATH).param("nickname", "운영").param("apiKey", FIRST_API_KEY));
        postAsAdmin(post(GOOGLE_KEYS_PATH).param("nickname", "예비").param("apiKey", SECOND_API_KEY));
        AiApiKeyService googleKeyService = findKeyService(KeyedAiProvider.GOOGLE);
        String googleSecondKeyId = googleKeyService.findStoredKeys().get(1).id();

        postAsAdmin(post(GOOGLE_KEYS_PATH + "/" + googleSecondKeyId + "/move").param("direction", "up"))
                .andExpect(redirectedUrl(AI_KEYS_PATH + "#google"));
        postAsAdmin(post(GOOGLE_KEYS_PATH + "/" + googleSecondKeyId + "/rename").param("nickname", "재발급"));
        postAsAdmin(post(GOOGLE_KEYS_PATH + "/" + googleSecondKeyId + "/replace").param("apiKey", REISSUED_API_KEY));
        postAsAdmin(post(GOOGLE_KEYS_PATH + "/" + googleSecondKeyId + "/toggle").param("enabled", "false"));
        assertThat(googleKeyService.findStoredKeys()).extracting(AiApiKey::nickname).containsExactly("재발급", "운영");
        assertThat(googleKeyService.findCallableKeys()).extracting(AiApiKey::apiKey).containsExactly(FIRST_API_KEY);

        // 다른 제공자 주소로는 이 키를 찾지 못함, Anthropic 목록은 그대로
        postAsAdmin(post(ANTHROPIC_KEYS_PATH + "/" + googleSecondKeyId + "/remove"))
                .andExpect(flash().attribute("flashError", containsString("AI_KEY_NOT_FOUND")));
        postAsAdmin(post(GOOGLE_KEYS_PATH + "/" + googleSecondKeyId + "/remove"))
                .andExpect(flash().attribute("flashMessage", containsString("Google 재발급 키를 삭제했어요")));
        assertThat(googleKeyService.findStoredKeys()).extracting(AiApiKey::nickname).containsExactly("운영");
        assertThat(findKeyService(KeyedAiProvider.ANTHROPIC).findStoredKeys().stream().map(AiApiKey::nickname).toList()).containsExactly("운영");
    }

    @Test
    void rejectsKeyChangeForUnknownOrKeylessProviderCode() throws Exception {
        postAsAdmin(post(AI_KEYS_PATH + "/mistral").param("nickname", "운영").param("apiKey", FIRST_API_KEY))
                .andExpect(redirectedUrl(AI_KEYS_PATH))
                .andExpect(flash().attribute("flashError", containsString("AI_PROVIDER_UNKNOWN")));
        // Ollama 는 키 목록이 없는 제공자
        postAsAdmin(post(AI_KEYS_PATH + "/ollama/nokey000/remove"))
                .andExpect(flash().attribute("flashError", containsString("AI_PROVIDER_UNKNOWN")));

        assertThat(jdbcTemplate.queryForObject("select count(*) from system_config", Long.class)).isZero();
    }

    @Test
    void savesProviderPriorityIncludingOpenAiAndGoogle() throws Exception {
        postAsAdmin(post(AI_KEYS_PATH + "/provider-priority").param("providerCodes", "google", "openai", "anthropic", "ollama"))
                .andExpect(redirectedUrl(AI_KEYS_PATH))
                .andExpect(flash().attribute("flashMessage", containsString("우선순위를 저장했어요")));

        assertThat(systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY)).isEqualTo("google,openai,anthropic,ollama");
        assertThat(findAuditLogs().get(0).getDetail()).isEqualTo("anthropic -> google,openai,anthropic,ollama");
        getAsAdmin(AI_KEYS_PATH).andExpect(content().string(containsString("4순위")));
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(adminUsername).roles("ADMIN")));
    }

    private ResultActions postAsAdmin(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user(adminUsername).roles("ADMIN")).with(csrf()));
    }

    private AiApiKeyService findKeyService(KeyedAiProvider provider) {
        return aiApiKeyServiceRegistry.find(provider);
    }

    // 이 테스트의 관리자가 남긴 감사 기록 (최신순)
    private List<AdminAuditLog> findAuditLogs() {
        return adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, RECENT_AUDIT_LOG_COUNT)).getContent().stream()
                .filter(auditLog -> adminUsername.equals(auditLog.getAdminUsername()))
                .toList();
    }
}
