package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.SourceCode;
import kr.suhsaechan.secbrief.advisory.entity.Source;
import kr.suhsaechan.secbrief.advisory.repository.SourceRepository;
import kr.suhsaechan.secbrief.application.admin.source.AdminSourceRow;
import kr.suhsaechan.secbrief.application.admin.source.AdminSourceService;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLog;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLogRepository;
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

/**
 * 출처 관리 (DB): 켜기·끄기와 주기 저장 + 감사 기록, 주기 범위(5~1440분)·숫자 아님·없는 출처 거부, 꺼진 출처도 표에 보임
 * 바꾼 출처는 테스트 뒤에 원래 값으로 되돌림 (다른 테스트가 같은 DB 를 씀)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSourceManagementTest extends PostgresContainerTestSupport {

    private static final String SOURCES_PATH = "/admin/sources";
    private static final int RECENT_AUDIT_LOG_COUNT = 10;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SourceRepository sourceRepository;
    @Autowired
    private AdminSourceService adminSourceService;
    @Autowired
    private AdminAuditLogRepository adminAuditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String adminUsername = AdminTestAccounts.randomUsername();
    private Source originalSource;

    @BeforeEach
    void rememberSource() {
        originalSource = sourceRepository.findByCode(SourceCode.BLEEPING_COMPUTER).orElseThrow();
    }

    @AfterEach
    void restoreSource() {
        jdbcTemplate.update("update source set is_enabled = ?, interval_minutes = ? where id = ?",
                originalSource.isEnabled(), originalSource.getIntervalMinutes(), originalSource.getId());
    }

    @Test
    void savesToggleAndIntervalAndRecordsAudit() throws Exception {
        // 켜짐 체크 상자를 끄면 enabled 값이 오지 않음
        postSourceUpdate(originalSource.getId(), null, "45")
                .andExpect(redirectedUrl(SOURCES_PATH))
                .andExpect(flash().attribute("flashMessage", containsString("저장했어요")));

        Source updatedSource = sourceRepository.findById(originalSource.getId()).orElseThrow();
        assertThat(updatedSource.isEnabled()).isFalse();
        assertThat(updatedSource.getIntervalMinutes()).isEqualTo(45);
        AdminAuditLog auditLog = latestSourceAuditLog();
        assertThat(auditLog.getTargetId()).isEqualTo(SourceCode.BLEEPING_COMPUTER);
        assertThat(auditLog.getTargetType()).isEqualTo("SOURCE");
        assertThat(auditLog.getDetail()).isEqualTo("enabled true -> false, intervalMinutes "
                + originalSource.getIntervalMinutes() + " -> 45");
    }

    @Test
    void showsDisabledSourceInAdminTable() throws Exception {
        postSourceUpdate(originalSource.getId(), null, String.valueOf(originalSource.getIntervalMinutes()));

        List<AdminSourceRow> sourceRows = adminSourceService.findAllSources();

        assertThat(sourceRows).hasSize((int) sourceRepository.count());
        assertThat(sourceRows).filteredOn(row -> row.code().equals(SourceCode.BLEEPING_COMPUTER))
                .singleElement().satisfies(row -> assertThat(row.isEnabled()).isFalse());
    }

    @Test
    void doesNotRecordAuditWhenNothingChanged() throws Exception {
        postSourceUpdate(originalSource.getId(), "true", String.valueOf(originalSource.getIntervalMinutes()))
                .andExpect(flash().attribute("flashMessage", containsString("그대로예요")));

        assertThat(adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, RECENT_AUDIT_LOG_COUNT)).getContent())
                .noneMatch(auditLog -> adminUsername.equals(auditLog.getAdminUsername()));
    }

    @Test
    void rejectsIntervalOutOfRange() throws Exception {
        for (String intervalMinutes : List.of("4", "1441", "abc", "")) {
            postSourceUpdate(originalSource.getId(), "true", intervalMinutes)
                    .andExpect(redirectedUrl(SOURCES_PATH))
                    .andExpect(flash().attribute("flashError", containsString("SOURCE_INTERVAL_OUT_OF_RANGE")));
        }

        assertThat(sourceRepository.findById(originalSource.getId()).orElseThrow().getIntervalMinutes())
                .isEqualTo(originalSource.getIntervalMinutes());
    }

    @Test
    void acceptsBoundaryIntervals() throws Exception {
        postSourceUpdate(originalSource.getId(), "true", "5");
        assertThat(sourceRepository.findById(originalSource.getId()).orElseThrow().getIntervalMinutes()).isEqualTo(5);

        postSourceUpdate(originalSource.getId(), "true", "1440");
        assertThat(sourceRepository.findById(originalSource.getId()).orElseThrow().getIntervalMinutes()).isEqualTo(1440);
    }

    @Test
    void rejectsUnknownSource() throws Exception {
        postSourceUpdate(999_999_999L, "true", "30")
                .andExpect(flash().attribute("flashError", containsString("RESOURCE_NOT_FOUND")));
    }

    private ResultActions postSourceUpdate(long sourceId, String enabled, String intervalMinutes) throws Exception {
        var request = post(SOURCES_PATH + "/" + sourceId).with(user(adminUsername).roles("ADMIN")).with(csrf())
                .param("intervalMinutes", intervalMinutes);
        if (enabled != null) {
            request.param("enabled", enabled);
        }
        return mockMvc.perform(request);
    }

    private AdminAuditLog latestSourceAuditLog() {
        return adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(0, RECENT_AUDIT_LOG_COUNT)).getContent().stream()
                .filter(auditLog -> auditLog.getAction() == AdminAuditAction.SOURCE_UPDATE
                        && adminUsername.equals(auditLog.getAdminUsername()))
                .findFirst().orElseThrow();
    }
}
