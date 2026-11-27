package kr.suhsaechan.secbrief.web.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.List;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigCache;
import kr.suhsaechan.secbrief.common.config.SystemConfigHistory;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.config.SystemConfigView;
import kr.suhsaechan.secbrief.common.crypto.SecretCipher;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 설정 저장소 (DB): 저장·이력·기본값 복원이 실제 테이블에 남음, 비밀값은 암호문으로만 저장, 검증 실패는 아무것도 남기지 않음
 * 테스트가 쓴 행은 앞뒤로 지움 (다른 테스트가 같은 DB 를 씀)
 */
@SpringBootTest
@ActiveProfiles("test")
class SystemConfigStoreTest extends PostgresContainerTestSupport {

    private static final String ACTOR = "config-store-test";
    private static final String SECRET_VALUE = "[{\"id\":\"a1b2c3d4\",\"nickname\":\"운영\",\"apiKey\":\"sk-test-store-000000000\",\"isEnabled\":true}]";

    @Autowired
    private SystemConfigService systemConfigService;
    @Autowired
    private SystemConfigCache systemConfigCache;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearStoredConfigs() {
        jdbcTemplate.update("delete from system_config");
        jdbcTemplate.update("delete from system_config_history");
        systemConfigCache.invalidate();
    }

    @Test
    void readsDeployPropertyOrCodeDefaultWhenNothingIsStored() {
        // application.yml 값 (batch-size 20, daily-limit 300) 과 ConfigKey 기본값
        assertThat(systemConfigService.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
        assertThat(systemConfigService.getInt(ConfigKey.AI_REQUEST_BATCH_SIZE)).isEqualTo(10);
        assertThat(systemConfigService.getInt(ConfigKey.SUMMARY_BATCH_SIZE)).isEqualTo(10);
        assertThat(systemConfigService.getInt(ConfigKey.SUMMARY_DAILY_LIMIT)).isEqualTo(300);
        assertThat(systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY)).isEqualTo("anthropic");
        assertThat(systemConfigService.getDecimal(ConfigKey.AI_DAILY_BUDGET_USD)).isEqualByComparingTo("-1");
        assertThat(systemConfigService.getBoolean(ConfigKey.URGENT_ALERT_ENABLED)).isTrue();
        assertThat(findView(ConfigKey.AI_TITLE_BATCH_SIZE).valueSource())
                .isEqualTo(SystemConfigView.ConfigValueSource.DEPLOY_PROPERTY);
        assertThat(findView(ConfigKey.AI_DAILY_BUDGET_USD).valueSource())
                .isEqualTo(SystemConfigView.ConfigValueSource.CODE_DEFAULT);
    }

    @Test
    void savesValueAndHistoryThenRestoresDefault() {
        assertThat(systemConfigService.update(ConfigKey.AI_TITLE_BATCH_SIZE, "50", ACTOR, "새 소식이 밀려서")).isTrue();

        assertThat(systemConfigService.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(50);
        assertThat(findStoredValue(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo("50");
        // BaseEntity 감사 열이 채워짐
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from system_config where created_at is not null and updated_at is not null", Long.class))
                .isEqualTo(1);

        assertThat(systemConfigService.update(ConfigKey.AI_TITLE_BATCH_SIZE, "80", ACTOR, null)).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from system_config", Long.class)).isEqualTo(1);
        assertThat(systemConfigService.resetToDefault(ConfigKey.AI_TITLE_BATCH_SIZE, ACTOR, "되돌림")).isTrue();

        assertThat(jdbcTemplate.queryForObject("select count(*) from system_config", Long.class)).isZero();
        assertThat(systemConfigService.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
        // 이력은 최신순
        List<SystemConfigHistory> histories = systemConfigService.findHistories(ConfigKey.AI_TITLE_BATCH_SIZE);
        assertThat(histories).extracting(SystemConfigHistory::getOldValue).containsExactly("80", "50", "20");
        assertThat(histories).extracting(SystemConfigHistory::getNewValue).containsExactly("20", "80", "50");
        assertThat(histories).extracting(SystemConfigHistory::getChangedBy).containsOnly(ACTOR);
        assertThat(histories).extracting(SystemConfigHistory::getReason).containsExactly("되돌림", null, "새 소식이 밀려서");
        assertThat(histories).allSatisfy(history -> assertThat(history.getCreatedAt()).isNotNull());
    }

    @Test
    void rejectedValueLeavesNoRowAndNoHistory() {
        assertThatThrownBy(() -> systemConfigService.update(ConfigKey.SUMMARY_DAILY_LIMIT, "-5", ACTOR, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);

        assertThat(jdbcTemplate.queryForObject("select count(*) from system_config", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from system_config_history", Long.class)).isZero();
    }

    @Test
    void storesSecretAsCipherTextOnlyAndHistoryAsPresence() {
        systemConfigService.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, ACTOR, "AI 키 추가: 운영");

        String storedValue = findStoredValue(ConfigKey.ANTHROPIC_API_KEYS);
        assertThat(storedValue).startsWith("enc:v1:").doesNotContain("sk-test-store");
        assertThat(systemConfigService.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEqualTo(SECRET_VALUE);
        List<String> historyTexts = jdbcTemplate.queryForList(
                "select coalesce(old_value, '') || '|' || coalesce(new_value, '') || '|' || coalesce(reason, '') "
                        + "from system_config_history", String.class);
        assertThat(historyTexts).containsExactly("없음|있음|AI 키 추가: 운영");
        SystemConfigView secretView = findView(ConfigKey.ANTHROPIC_API_KEYS);
        assertThat(secretView.hasSecretValue()).isTrue();
        assertThat(secretView.toString()).doesNotContain("sk-test-store").doesNotContain("enc:v1:");
    }

    @Test
    void cipherTextMovedToAnotherRowCannotBeRead() {
        // 다른 설정 키 행의 암호문을 옮겨 붙임 (AAD = 설정 키 이름 불일치)
        systemConfigService.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, ACTOR, null);
        jdbcTemplate.update("update system_config set config_key = 'KEY_FROM_ANOTHER_ROW' where config_key = ?",
                ConfigKey.ANTHROPIC_API_KEYS.name());
        String movedCipherText = jdbcTemplate.queryForObject(
                "select config_value from system_config where config_key = 'KEY_FROM_ANOTHER_ROW'", String.class);
        systemConfigService.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE.replace("운영", "예비"), ACTOR, null);
        // 변조: 디코딩한 바이트 1개 뒤집기 (base64 글자를 바꾸면 끝 여분 비트에 걸려 변조가 안 될 수 있음)
        String tamperedCipherText = toTamperedCipherText(findStoredValue(ConfigKey.ANTHROPIC_API_KEYS));
        jdbcTemplate.update("update system_config set config_value = ? where config_key = ?",
                tamperedCipherText, ConfigKey.ANTHROPIC_API_KEYS.name());
        systemConfigCache.invalidate();

        assertThat(systemConfigService.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEmpty();
        assertThat(movedCipherText).startsWith("enc:v1:");
        // 모르는 키 이름의 행이 있어도 조회는 됨
        assertThat(systemConfigService.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
    }

    // 암호문 > 가운데 바이트 1개를 뒤집은 암호문 (접두어 유지)
    private static String toTamperedCipherText(String storedValue) {
        byte[] payload = Base64.getDecoder().decode(storedValue.substring(SecretCipher.ENCRYPTED_PREFIX.length()));
        payload[payload.length / 2] ^= 0x01;
        return SecretCipher.ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(payload);
    }

    private String findStoredValue(ConfigKey key) {
        return jdbcTemplate.queryForObject("select config_value from system_config where config_key = ?",
                String.class, key.name());
    }

    private SystemConfigView findView(ConfigKey key) {
        return systemConfigService.findViews().stream().filter(view -> view.key() == key).findFirst().orElseThrow();
    }
}
