package kr.suhsaechan.secbrief.web.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javax.sql.DataSource;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V0.8.1 마이그레이션: 이전 스키마(V0.6.0)에 기기·긴급 기사 행이 있는 상태에서 올려도 성공,
 * 기존 기기는 방해 금지 시간 꺼짐(22:00~08:00, Asia/Seoul), 알림함·보류 표는 비어 있음
 * 앱 스키마와 섞이지 않게 별도 스키마에서 Flyway 를 직접 돌림
 */
class NotificationInboxMigrationTest extends PostgresContainerTestSupport {

    private static final String MIGRATION_SCHEMA = "migration_check_0_8_1";
    private static final String PREVIOUS_VERSION = "0.6.0";

    @Test
    void migratesPreviousSchemaWithExistingDevices() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=" + MIGRATION_SCHEMA, POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop schema if exists " + MIGRATION_SCHEMA + " cascade");

        // 이전 버전까지 > 기존 행 > 이번 버전까지
        toFlyway(dataSource).target(PREVIOUS_VERSION).load().migrate();
        jdbcTemplate.update("insert into push_device (push_token, platform) values ('ExponentPushToken[existing]', 'ANDROID')");
        jdbcTemplate.update("insert into push_device_alert_category (push_device_id, alert_category) "
                + "select id, 'KEV' from push_device");
        toFlyway(dataSource).load().migrate();

        Map<String, Object> existingDevice = jdbcTemplate.queryForMap("select is_quiet_hours_enabled, "
                + "to_char(quiet_hours_start_time, 'HH24:MI') as start_time, to_char(quiet_hours_end_time, 'HH24:MI') as end_time, "
                + "time_zone from push_device");
        assertThat(existingDevice).containsEntry("is_quiet_hours_enabled", false).containsEntry("start_time", "22:00")
                .containsEntry("end_time", "08:00").containsEntry("time_zone", "Asia/Seoul");
        assertThat(jdbcTemplate.queryForObject("select count(*) from notification", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from push_device_held_push", Long.class)).isZero();

        jdbcTemplate.execute("drop schema " + MIGRATION_SCHEMA + " cascade");
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration toFlyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).schemas(MIGRATION_SCHEMA).createSchemas(true)
                .locations("classpath:db/migration");
    }
}
