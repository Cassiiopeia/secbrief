package kr.suhsaechan.secbrief.web.migration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V0.9.0 마이그레이션: 이전 스키마(V0.8.1)의 기사 제목에 남은 &#039; 풀기 (운영 기사 800), 다른 제목은 그대로,
 * 출처에 채우기 끝 시각 열 추가 (기존 출처는 null = 채우기 전)
 */
class SourceBackfillMigrationTest extends PostgresContainerTestSupport {

    private static final String MIGRATION_SCHEMA = "migration_check_0_9_0";
    private static final String PREVIOUS_VERSION = "0.8.1";

    @Test
    void unescapesRemainingEntitiesAndAddsBackfillColumn() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=" + MIGRATION_SCHEMA, POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop schema if exists " + MIGRATION_SCHEMA + " cascade");

        // 이전 버전까지 > 운영과 같은 제목 > 이번 버전까지
        toFlyway(dataSource).target(PREVIOUS_VERSION).load().migrate();
        insertArticle(jdbcTemplate, "국내·외 사이버 위협 우려가 고조됨에 따라 &#039;주의&#039; 경보 발령", "https://example.test/800");
        insertArticle(jdbcTemplate, "사이버위기 경보 단계 '관심' → '주의' 상향 조정", "https://example.test/794");
        toFlyway(dataSource).load().migrate();

        assertThat(jdbcTemplate.queryForList("select title from article order by original_url", String.class))
                .containsExactly("사이버위기 경보 단계 '관심' → '주의' 상향 조정", "국내·외 사이버 위협 우려가 고조됨에 따라 '주의' 경보 발령");
        assertThat(jdbcTemplate.queryForObject("select count(*) from source where backfill_completed_at is not null", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from source where code like 'KISA%'", Long.class)).isEqualTo(4);

        jdbcTemplate.execute("drop schema " + MIGRATION_SCHEMA + " cascade");
    }

    private static void insertArticle(JdbcTemplate jdbcTemplate, String title, String originalUrl) {
        jdbcTemplate.update("insert into article (source_id, kind, title, original_url, publisher_name, published_at, region, "
                + "topic, ai_status, is_kev, is_urgent, collected_at, created_at, updated_at) "
                + "select id, 'ADVISORY', ?, ?, 'KISA', now(), 'DOMESTIC', 'GENERAL', 'PENDING', false, false, now(), now(), now() "
                + "from source where code = 'KISA_ALERT_LEVEL'", title, originalUrl);
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration toFlyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).schemas(MIGRATION_SCHEMA).createSchemas(true)
                .locations("classpath:db/migration");
    }
}
