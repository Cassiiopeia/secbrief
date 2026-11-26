package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import kr.suhsaechan.secbrief.advisory.catalog.TagCatalogProvider;
import kr.suhsaechan.secbrief.advisory.constant.CatalogTagKind;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 분류 태그 관리 (DB): 추가 > 앱 API 에 바로 보임, 코드 모양·중복·없는 분야 거부, 숨기기 > API·분류에서 빠짐, 감사 기록
 * 추가한 항목은 테스트 뒤에 지움 (다른 테스트가 같은 시드를 씀)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCatalogTagManagementTest extends PostgresContainerTestSupport {

    private static final String CATALOG_PATH = "/admin/catalog-tags";
    private static final String NEW_PRODUCT_CODE = "test-gateway";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TagCatalogProvider tagCatalogProvider;

    private final String adminUsername = AdminTestAccounts.randomUsername();

    @AfterEach
    void removeAddedTagAndRestoreSeed() {
        jdbcTemplate.update("delete from catalog_tag where code = ?", NEW_PRODUCT_CODE);
        jdbcTemplate.update("update catalog_tag set is_visible = true where kind = 'FIELD' and code = 'ics-iot'");
        tagCatalogProvider.invalidate();
    }

    @Test
    void addsProductThatAppAndClassifierSeeRightAway() throws Exception {
        mockMvc.perform(post(CATALOG_PATH).with(user(adminUsername).roles("ADMIN")).with(csrf())
                        .param("kind", "PRODUCT").param("code", NEW_PRODUCT_CODE).param("name", "Test Gateway")
                        .param("fieldCode", "network").param("matchTerms", "test gateway, tgw").param("isWidelyUsed", "true")
                        .param("displayOrder", "700"))
                .andExpect(redirectedUrl(CATALOG_PATH + "?kind=PRODUCT"))
                .andExpect(flash().attribute("flashMessage", containsString("추가했어요")));

        assertThat(tagCatalogProvider.getCatalog().find(CatalogTagKind.PRODUCT, NEW_PRODUCT_CODE))
                .hasValueSatisfying(entry -> {
                    assertThat(entry.shortName()).isEqualTo("Test Gateway");
                    assertThat(entry.isWidelyUsed()).isTrue();
                });
        mockMvc.perform(get("/api/v1/catalog/tags"))
                .andExpect(jsonPath("$.data.tags[?(@.code == '" + NEW_PRODUCT_CODE + "')].fieldCode").value("network"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from admin_audit_log where action = 'CATALOG_TAG_CREATE' "
                + "and admin_username = ?", Long.class, adminUsername)).isEqualTo(1);
    }

    @Test
    void rejectsBadCodeDuplicateAndUnknownField() throws Exception {
        postCreate("Bad Code", "network").andExpect(flash().attribute("flashError", containsString("CATALOG_TAG_CODE_INVALID")));
        postCreate("fortinet", "network")
                .andExpect(flash().attribute("flashError", containsString("CATALOG_TAG_CODE_DUPLICATED")));
        postCreate(NEW_PRODUCT_CODE, "no-such-field")
                .andExpect(flash().attribute("flashError", containsString("CATALOG_TAG_REFERENCE_INVALID")));
    }

    @Test
    void hidesFieldFromAppApiAndCatalog() throws Exception {
        Long fieldId = jdbcTemplate.queryForObject("select id from catalog_tag where kind = 'FIELD' and code = 'ics-iot'",
                Long.class);

        mockMvc.perform(post(CATALOG_PATH + "/" + fieldId + "/visibility").with(user(adminUsername).roles("ADMIN"))
                        .with(csrf()).param("kind", "FIELD").param("visible", "false"))
                .andExpect(flash().attribute("flashMessage", containsString("숨겼어요")));

        assertThat(tagCatalogProvider.getCatalog().hasCode(CatalogTagKind.FIELD, "ics-iot")).isFalse();
        mockMvc.perform(get("/api/v1/catalog/tags"))
                .andExpect(jsonPath("$.data.tags[?(@.code == 'ics-iot')]").isEmpty());
    }

    private ResultActions postCreate(String code, String fieldCode) throws Exception {
        return mockMvc.perform(post(CATALOG_PATH).with(user(adminUsername).roles("ADMIN")).with(csrf())
                .param("kind", "PRODUCT").param("code", code).param("name", "Test").param("fieldCode", fieldCode));
    }
}
