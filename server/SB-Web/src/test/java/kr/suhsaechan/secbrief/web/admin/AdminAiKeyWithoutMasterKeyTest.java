package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalyzer;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKey;
import kr.suhsaechan.secbrief.advisory.ai.key.AnthropicApiKeyService;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 마스터 키 없이 띄운 서버 (DB): 기동·화면은 되고, AI 키 화면에 안내 배너와 함께 추가가 막힘, 키를 평문으로 저장하지 않음
 * 운영 설정의 키(쉼표로 여러 개)는 그대로 fallback 으로 씀
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "secbrief.secret.master-key=",
        "secbrief.ai.anthropic-api-key=sk-test-deploy-first-000000000-EEEE, sk-test-deploy-second-00000000-FFFF"})
class AdminAiKeyWithoutMasterKeyTest extends PostgresContainerTestSupport {

    private static final String AI_KEYS_PATH = "/admin/ai-keys";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AnthropicApiKeyService anthropicApiKeyService;
    @Autowired
    private ArticleTitleAnalyzer articleTitleAnalyzer;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String adminUsername = AdminTestAccounts.randomUsername();

    @Test
    void showsGuideBannerAndDisablesAddForm() throws Exception {
        mockMvc.perform(get(AI_KEYS_PATH).with(user(adminUsername).roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("SECRET_MASTER_KEY_MISSING")))
                .andExpect(content().string(containsString("secbrief.secret.master-key")))
                .andExpect(content().string(containsString("disabled")))
                // 운영 설정의 키: 가린 값만, 읽기 전용 표
                .andExpect(content().string(containsString("운영 설정의 키")))
                .andExpect(content().string(containsString("••••EEEE")))
                .andExpect(content().string(containsString("••••FFFF")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("sk-test-deploy"))));
        mockMvc.perform(get("/admin/settings").with(user(adminUsername).roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("SECRET_MASTER_KEY_MISSING")));
    }

    @Test
    void refusesToAddKeyInsteadOfStoringPlainText() throws Exception {
        mockMvc.perform(post(AI_KEYS_PATH).with(user(adminUsername).roles("ADMIN")).with(csrf())
                        .param("nickname", "운영").param("apiKey", "sk-test-page-first-00000000000-AAAA"))
                .andExpect(flash().attribute("flashError", containsString("SECRET_MASTER_KEY_MISSING")));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from system_config where config_key = 'ANTHROPIC_API_KEYS'", Long.class)).isZero();
    }

    @Test
    void usesDeployPropertyKeysAsFallback() {
        assertThat(anthropicApiKeyService.isUsingDeployPropertyKeys()).isTrue();
        assertThat(anthropicApiKeyService.findCallableKeys()).extracting(AnthropicApiKey::nickname)
                .containsExactly("config", "config-2");
        // 제목 분석이 이 키들로 호출 가능 상태 (실제 호출은 하지 않음)
        assertThat(articleTitleAnalyzer.isAvailable()).isTrue();
    }
}
