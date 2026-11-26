package kr.suhsaechan.secbrief.web.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 브리핑 열 마이그레이션(V0.8.0): 이전 스키마(V0.6.0)에 이전 구조의 요약 행이 있는 DB 위에 적용
 * 기존 행은 값이 그대로이고 브리핑 열만 null 로 생김 (데이터 이전 없음), 그 뒤 브리핑을 쓸 수 있음
 */
@Testcontainers(disabledWithoutDocker = true)
class ArticleBriefMigrationTest {

    // 브리핑 열이 생기기 직전의 운영 스키마 버전
    private static final String PREVIOUS_SCHEMA_VERSION = "0.6.0";
    private static final String BRIEF_SCHEMA_VERSION = "0.8.0";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    private static final String INSERT_LEGACY_ARTICLE = """
            insert into article (source_id, kind, title, original_url, publisher_name, published_at,
                                 is_published_at_estimated, region, is_kev, is_urgent, collected_at,
                                 summary, summary_key_points, summary_action_guide, summary_status)
            values ((select id from source where code = 'NVD'), 'VULNERABILITY', 'CVE-0000-00001: Example Gateway', '%s',
                    'NVD', now(), false, 'OVERSEAS', false, false, now(), %s, %s, %s, '%s')
            """;
    private static final String SELECT_SUMMARY_COLUMNS = """
            select summary, summary_key_points, summary_action_guide, summary_status,
                   summary_one_line, summary_what_happened, summary_why_it_matters, summary_affected, summary_actions
              from article where original_url = '%s'
            """;

    @Test
    void addsNullBriefColumnsAndKeepsExistingSummaryRows() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            // 이전 스키마까지만 적용 > 이전 구조의 행 2건 (요약 있음 DONE, 요약 없음 NONE)
            migrate(postgres, PREVIOUS_SCHEMA_VERSION);
            try (Connection connection = connect(postgres); Statement statement = connection.createStatement()) {
                statement.execute(INSERT_LEGACY_ARTICLE.formatted("https://example.test/done",
                        "'이전 구조로 쓴 요약이에요.'", "'[\"첫째예요.\",\"둘째예요.\"]'", "'패치를 적용하세요.'", "DONE"));
                statement.execute(INSERT_LEGACY_ARTICLE.formatted("https://example.test/none", "null", "null", "null", "NONE"));
            }

            // 브리핑 열 추가 버전까지 적용 (이후 마이그레이션과 무관하게 이 버전만 확인)
            String schemaVersion = migrate(postgres, BRIEF_SCHEMA_VERSION);

            assertThat(schemaVersion).isEqualTo(BRIEF_SCHEMA_VERSION);
            try (Connection connection = connect(postgres); Statement statement = connection.createStatement()) {
                assertThat(readSummaryColumns(statement, "https://example.test/done")).containsExactly(
                        "이전 구조로 쓴 요약이에요.", "[\"첫째예요.\",\"둘째예요.\"]", "패치를 적용하세요.", "DONE",
                        null, null, null, null, null);
                assertThat(readSummaryColumns(statement, "https://example.test/none")).containsExactly(
                        null, null, null, "NONE", null, null, null, null, null);

                // 새 열은 값을 받음 (길이: 한 줄 결론 200, 무슨 일 600, 왜 중요 500)
                statement.execute("update article set summary_one_line = repeat('가', 200), summary_what_happened = repeat('나', 600), "
                        + "summary_why_it_matters = repeat('다', 500), summary_affected = '[]', summary_actions = '[]' "
                        + "where original_url = 'https://example.test/done'");
                assertThat(readSummaryColumns(statement, "https://example.test/done").get(4)).hasSize(200);
            }
        }
    }

    // target 이 null 이면 최신까지. 반환: 적용 뒤 스키마 버전
    private static String migrate(PostgreSQLContainer<?> postgres, String targetVersion) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(MIGRATION_LOCATION);
        if (targetVersion != null) {
            configuration.target(targetVersion);
        }
        Flyway flyway = configuration.load();
        flyway.migrate();
        return flyway.info().current().getVersion().getVersion();
    }

    private static Connection connect(PostgreSQLContainer<?> postgres) throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static List<String> readSummaryColumns(Statement statement, String originalUrl) throws Exception {
        try (ResultSet row = statement.executeQuery(SELECT_SUMMARY_COLUMNS.formatted(originalUrl))) {
            row.next();
            List<String> values = new ArrayList<>();
            for (int columnIndex = 1; columnIndex <= 9; columnIndex++) {
                values.add(row.getString(columnIndex));
            }
            return values;
        }
    }
}
