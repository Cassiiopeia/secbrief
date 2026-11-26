package kr.suhsaechan.secbrief.web.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.ArticleTestWriter;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 관리자 화면 전체 렌더링 (DB): 로그인 뒤 화면 11개가 빈 표와 행이 있는 표 모두에서 200 + 틀·제목·좁은 화면 표
 * 행이 있을 때만 그려지는 칸(배지·조각 인자)의 템플릿 오류를 잡음 (#70 에서 행이 있을 때만 500)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ArticleTestWriter.class)
class AdminAllPagesRenderTest extends PostgresContainerTestSupport {

    private static final String ADMIN_USERNAME = AdminTestAccounts.randomUsername();
    // 수집 실패로 바꿨다가 되돌릴 출처
    private static final String FAILING_SOURCE_CODE = "UBUNTU_USN";
    private static final String HISTORY_CONFIG_KEY = "SUMMARY_DAILY_LIMIT";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ArticleTestWriter articleTestWriter;

    private Map<String, Object> originalFailingSourceState;

    @BeforeEach
    void clearAdminListTables() {
        jdbcTemplate.update("delete from notification");
        jdbcTemplate.update("delete from ai_call_log");
        jdbcTemplate.update("delete from push_device");
        jdbcTemplate.update("delete from system_config_history where config_key = ?", HISTORY_CONFIG_KEY);
        originalFailingSourceState = jdbcTemplate.queryForMap(
                "select is_enabled, last_status from source where code = ?", FAILING_SOURCE_CODE);
    }

    @AfterEach
    void restoreSharedRows() {
        jdbcTemplate.update("update source set is_enabled = ?, last_status = ? where code = ?",
                originalFailingSourceState.get("is_enabled"), originalFailingSourceState.get("last_status"), FAILING_SOURCE_CODE);
        jdbcTemplate.update("delete from collect_run where error_code = 'RENDER_TEST_FAILURE'");
        // 다른 테스트 클래스가 보는 표는 비워 둠 (알림 기록·기기)
        jdbcTemplate.update("delete from notification");
        jdbcTemplate.update("delete from push_device");
    }

    @Test
    void rendersEveryPageWithEmptyTables() throws Exception {
        for (String pagePath : listPagePaths(null)) {
            expectAdminPage(pagePath);
        }
        getAsAdmin("/admin/notices").andExpect(content().string(containsString("아직 올린 공지사항이 없어요")));
        getAsAdmin("/admin/alerts").andExpect(content().string(containsString("최근 30일 동안 보낸 긴급 알림이 없어요")));
        getAsAdmin("/admin/ai-calls").andExpect(content().string(containsString("아직 남은 호출 기록이 없어요")));
        getAsAdmin("/admin/settings/" + HISTORY_CONFIG_KEY + "/history")
                .andExpect(content().string(containsString("아직 바꾼 적이 없어요")));
    }

    @Test
    void rendersEveryPageWithRows() throws Exception {
        long noticeId = seedRows();

        for (String pagePath : listPagePaths(noticeId)) {
            expectAdminPage(pagePath);
        }
        // 확인할 일: 켜진 출처의 수집 실패 (대시보드 숫자는 60초 캐시라 출처 상태로 확인)
        getAsAdmin("/admin").andExpect(content().string(containsString("확인할 일")))
                .andExpect(content().string(containsString("마지막 수집이 실패했어요")))
                .andExpect(content().string(containsString("href=\"/admin/notices\"")));
        getAsAdmin("/admin/sources").andExpect(content().string(containsString("RENDER_TEST_FAILURE: render failure")))
                .andExpect(content().string(containsString("role=\"alert\"")));
        getAsAdmin("/admin/notices").andExpect(content().string(containsString("렌더 확인 공지")))
                .andExpect(content().string(containsString("data-label=\"올린 시각\"")));
        getAsAdmin("/admin/notices/" + noticeId + "/edit").andExpect(content().string(containsString("렌더 확인 공지")));
        getAsAdmin("/admin/alerts").andExpect(content().string(containsString("렌더 확인 긴급 알림")))
                .andExpect(content().string(containsString("방해 금지로 보류 중")));
        getAsAdmin("/admin/ai-calls").andExpect(content().string(containsString("render-model")));
        getAsAdmin("/admin/settings/" + HISTORY_CONFIG_KEY + "/history").andExpect(content().string(containsString("렌더 확인 이유")));
    }

    // 목록 화면마다 1행 이상: 공지, 긴급 알림(보냄·보류), AI 호출(성공·실패), 알림 기기, 설정 이력, 수집 실패 출처, 요약 실패 기사
    private long seedRows() {
        Article article = articleTestWriter.saveNews(Instant.now(), ArticleTopic.GENERAL);
        jdbcTemplate.update("update article set summary_status = 'FAILED' where id = ?", article.getId());
        jdbcTemplate.update("insert into notification (kind, title, body, link_url, is_pinned, pushed_at) "
                + "values ('NOTICE', '렌더 확인 공지', '줄 1\n줄 2', 'https://example.test', true, now())");
        jdbcTemplate.update("insert into notification (kind, alert_category, title, body, pushed_at) "
                + "values ('ALERT', 'KEV', '렌더 확인 긴급 알림', '본문', now())");
        jdbcTemplate.update("insert into notification (kind, alert_category, title, body, pushed_at) "
                + "values ('ALERT', 'KISA_URGENT', '렌더 확인 긴급 알림 보류', '본문', null)");
        jdbcTemplate.update("insert into notification_article (notification_id, position, article_id, alert_category) "
                + "select id, 0, ?, alert_category from notification where kind = 'ALERT'", article.getId());
        jdbcTemplate.update("insert into ai_call_log (call_type, provider, model, key_label, is_success, failure_reason, "
                + "latency_ms, input_tokens, output_tokens, estimated_cost_usd) values "
                + "('SUMMARY', 'anthropic', 'render-model', 'render', true, null, 1200, 100, 50, 0.0001), "
                + "('TITLE_ANALYSIS', 'anthropic', 'render-model', 'render', false, 'RATE_LIMITED', 300, null, null, null)");
        jdbcTemplate.update("insert into push_device (push_token, platform, created_at, updated_at) "
                + "values ('ExponentPushToken[render-test]', 'ANDROID', now(), now())");
        jdbcTemplate.update("insert into system_config_history (config_key, old_value, new_value, changed_by, reason) "
                + "values (?, '300', '200', ?, '렌더 확인 이유')", HISTORY_CONFIG_KEY, ADMIN_USERNAME);
        jdbcTemplate.update("update source set is_enabled = true, last_status = 'FAILED' where code = ?", FAILING_SOURCE_CODE);
        jdbcTemplate.update("insert into collect_run (source_id, started_at, finished_at, status, new_count, error_code, error_message) "
                + "select id, now(), now(), 'FAILED', 0, 'RENDER_TEST_FAILURE', 'render failure' from source where code = ?",
                FAILING_SOURCE_CODE);
        return jdbcTemplate.queryForObject("select id from notification where kind = 'NOTICE'", Long.class);
    }

    // 로그인 뒤 화면 전체 (공지 고치기는 공지가 있을 때만)
    private static List<String> listPagePaths(Long noticeId) {
        List<String> pagePaths = new ArrayList<>(List.of("/admin", "/admin/sources", "/admin/notices", "/admin/alerts",
                "/admin/settings", "/admin/settings/" + HISTORY_CONFIG_KEY + "/history", "/admin/ai-keys", "/admin/ai-calls",
                "/admin/audit", "/admin/password"));
        if (noticeId != null) {
            pagePaths.add("/admin/notices/" + noticeId + "/edit");
        }
        return pagePaths;
    }

    // 공통: 200, 틀(사이드바·본문 건너뛰기), 화면 제목 h1
    private void expectAdminPage(String pagePath) throws Exception {
        getAsAdmin(pagePath).andExpect(status().isOk())
                .andExpect(content().string(containsString("본문으로 건너뛰기")))
                .andExpect(content().string(containsString("id=\"adminMainContent\"")))
                .andExpect(content().string(containsString("<h1 class=\"text-2xl font-bold\">")));
    }

    private ResultActions getAsAdmin(String path) throws Exception {
        return mockMvc.perform(get(path).with(user(ADMIN_USERNAME).roles("ADMIN")));
    }
}
