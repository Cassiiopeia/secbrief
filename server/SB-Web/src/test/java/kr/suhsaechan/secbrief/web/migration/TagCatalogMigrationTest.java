package kr.suhsaechan.secbrief.web.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V0.9.0 마이그레이션: 이전 스키마(V0.8.1)에 기사·기기·알림 기록이 있는 상태에서 올려도 성공
 * 긴급 아닌 기존 기사는 알림 처리됨(새 관심 판정에 걸리지 않음), 알림 대기 긴급 기사는 그대로, 모든 기사 분류 대기,
 * 세 종류를 다 켠 기기만 그 밖의 고위험을 끔, 기존 기기 받는 방식은 아침 요약 08:00, 카탈로그 시드 13·9·4 개
 */
class TagCatalogMigrationTest extends PostgresContainerTestSupport {

    private static final String MIGRATION_SCHEMA = "migration_check_0_9_0";
    private static final String PREVIOUS_VERSION = "0.8.1";

    @Test
    void migratesPreviousSchemaWithExistingRows() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=" + MIGRATION_SCHEMA, POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop schema if exists " + MIGRATION_SCHEMA + " cascade");

        // 이전 버전까지 > 기존 행 > 이번 버전까지
        toFlyway(dataSource).target(PREVIOUS_VERSION).load().migrate();
        insertArticle(jdbcTemplate, "https://example.test/routine", false, false);
        insertArticle(jdbcTemplate, "https://example.test/urgent-pending", true, false);
        insertArticle(jdbcTemplate, "https://example.test/urgent-notified", true, true);
        insertDevice(jdbcTemplate, "ExponentPushToken[all-three]", List.of("KEV", "KISA_URGENT", "CRITICAL_CVE"));
        insertDevice(jdbcTemplate, "ExponentPushToken[critical-only]", List.of("CRITICAL_CVE"));
        jdbcTemplate.update("insert into notification (kind, alert_category, title, body) values ('ALERT', 'KEV', '제목', '본문')");
        toFlyway(dataSource).load().migrate();

        Map<String, Boolean> isProcessedByUrl = Map.of(
                "https://example.test/routine", true,
                "https://example.test/urgent-pending", false,
                "https://example.test/urgent-notified", true);
        isProcessedByUrl.forEach((url, isProcessed) -> assertThat(jdbcTemplate.queryForObject(
                "select urgent_notified_at is not null from article where original_url = ?", Boolean.class, url))
                .as(url).isEqualTo(isProcessed));
        assertThat(jdbcTemplate.queryForObject("select count(*) from article where classified_at is null and is_notable = false",
                Long.class)).isEqualTo(3);
        assertThat(categoriesOf(jdbcTemplate, "ExponentPushToken[all-three]")).containsExactlyInAnyOrder("KEV", "KISA_URGENT");
        assertThat(categoriesOf(jdbcTemplate, "ExponentPushToken[critical-only]")).containsExactly("CRITICAL_CVE");
        assertThat(jdbcTemplate.queryForList("select distinct alert_delivery_mode || ' ' || to_char(digest_time, 'HH24:MI') "
                + "from push_device", String.class)).containsExactly("DIGEST 08:00");
        assertThat(jdbcTemplate.queryForObject("select count(*) from notification where push_device_id is null", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForList("select kind || ':' || count(*) from catalog_tag where kind <> 'PRODUCT' "
                + "group by kind order by kind", String.class))
                .containsExactly("ECOSYSTEM:9", "FIELD:13", "PRODUCT_GROUP:4");
        // 제품의 분야·묶음은 모두 있는 code
        assertThat(jdbcTemplate.queryForObject("select count(*) from catalog_tag p where p.kind = 'PRODUCT' and ("
                + "not exists (select 1 from catalog_tag f where f.kind = 'FIELD' and f.code = p.field_code) or "
                + "(p.group_code is not null and not exists (select 1 from catalog_tag g where g.kind = 'PRODUCT_GROUP' "
                + "and g.code = p.group_code)))", Long.class)).isZero();

        jdbcTemplate.execute("drop schema " + MIGRATION_SCHEMA + " cascade");
    }

    private static void insertArticle(JdbcTemplate jdbcTemplate, String originalUrl, boolean isUrgent, boolean isNotified) {
        jdbcTemplate.update("""
                insert into article (source_id, kind, title, original_url, publisher_name, published_at,
                                     is_published_at_estimated, region, is_kev, is_urgent, collected_at, urgent_notified_at)
                values ((select id from source where code = 'NVD'), 'VULNERABILITY', 'CVE-0000-00001: Example', ?, 'NVD', now(),
                        false, 'OVERSEAS', false, ?, now(), case when ? then now() else null end)""",
                originalUrl, isUrgent, isNotified);
    }

    private static void insertDevice(JdbcTemplate jdbcTemplate, String pushToken, List<String> categories) {
        jdbcTemplate.update("insert into push_device (push_token, platform) values (?, 'ANDROID')", pushToken);
        categories.forEach(category -> jdbcTemplate.update("insert into push_device_alert_category (push_device_id, alert_category) "
                + "select id, ? from push_device where push_token = ?", category, pushToken));
    }

    private static List<String> categoriesOf(JdbcTemplate jdbcTemplate, String pushToken) {
        return jdbcTemplate.queryForList("select c.alert_category from push_device_alert_category c join push_device d "
                + "on d.id = c.push_device_id where d.push_token = ?", String.class, pushToken);
    }

    private static FluentConfiguration toFlyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).schemas(MIGRATION_SCHEMA).createSchemas(true)
                .locations("classpath:db/migration");
    }
}
