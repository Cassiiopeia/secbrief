package kr.suhsaechan.secbrief.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kr.suhsaechan.secbrief.common.config.SystemConfigView.ConfigValueSource;
import kr.suhsaechan.secbrief.common.crypto.SecretCipher;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 설정 저장소의 비밀값 (메모리 가짜 저장소): 암호문으로만 저장, 화면용 값은 가림, 이력은 있음·없음만,
 * 마스터 키가 없으면 저장 거부·읽기는 없음, 다른 설정 키의 암호문은 읽지 못함
 */
class SystemConfigSecretTest {

    private static final String ACTOR = "admin";
    private static final String SECRET_VALUE = "[{\"id\":\"a1b2c3d4\",\"nickname\":\"운영\",\"apiKey\":\"sk-test-000\",\"isEnabled\":true}]";

    private final InMemorySystemConfigStore store = new InMemorySystemConfigStore();
    private final SystemConfigService service = store.createService();

    @Test
    void storesSecretEncryptedAndReadsItBack() {
        assertThat(service.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, ACTOR, "AI 키 추가: 운영")).isTrue();

        String storedValue = store.storedConfigs.get(0).getConfigValue();
        assertThat(storedValue).startsWith("enc:v1:").doesNotContain("sk-test");
        assertThat(service.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEqualTo(SECRET_VALUE);
        // 일반 조회로는 비밀값이 나오지 않음
        assertThat(service.getString(ConfigKey.ANTHROPIC_API_KEYS)).isEmpty();
    }

    @Test
    void secretHistoryShowsOnlyPresence() {
        service.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, ACTOR, "AI 키 추가: 운영");
        service.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE.replace("sk-test-000", "sk-test-111"), ACTOR, "AI 키 값 교체: 운영");
        service.resetToDefault(ConfigKey.ANTHROPIC_API_KEYS, ACTOR, "AI 키 삭제: 운영");

        assertThat(store.savedHistories).extracting(SystemConfigHistory::getOldValue).containsExactly("없음", "있음", "있음");
        assertThat(store.savedHistories).extracting(SystemConfigHistory::getNewValue).containsExactly("있음", "있음", "없음");
        assertThat(store.savedHistories).allSatisfy(history -> {
            assertThat(history.getOldValue()).doesNotContain("sk-test").doesNotContain("enc:v1:");
            assertThat(history.getNewValue()).doesNotContain("sk-test").doesNotContain("enc:v1:");
        });
        assertThat(service.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEmpty();
    }

    @Test
    void blankSecretKeepsStoredValue() {
        service.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, ACTOR, null);

        assertThat(service.update(ConfigKey.ANTHROPIC_API_KEYS, "  ", ACTOR, null)).isFalse();

        assertThat(service.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEqualTo(SECRET_VALUE);
        assertThat(store.savedHistories).hasSize(1);
    }

    @Test
    void viewsNeverExposeSecretPlainTextOrCipherText() {
        service.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, ACTOR, null);
        service.update(ConfigKey.AI_TITLE_BATCH_SIZE, "50", ACTOR, null);
        store.environment.setProperty("secbrief.summary.daily-limit", "150");

        List<SystemConfigView> views = service.findViews();

        assertThat(views).extracting(SystemConfigView::key).containsExactly(ConfigKey.values());
        SystemConfigView secretView = InMemorySystemConfigStore.findView(views, ConfigKey.ANTHROPIC_API_KEYS);
        assertThat(secretView.hasSecretValue()).isTrue();
        assertThat(secretView.isStored()).isTrue();
        assertThat(secretView.displayValue()).isEqualTo("••••");
        assertThat(views).allSatisfy(view -> assertThat(view.toString()).doesNotContain("sk-test").doesNotContain("enc:v1:"));

        SystemConfigView storedView = InMemorySystemConfigStore.findView(views, ConfigKey.AI_TITLE_BATCH_SIZE);
        assertThat(storedView.displayValue()).isEqualTo("50");
        assertThat(storedView.defaultValue()).isEqualTo("20");
        assertThat(storedView.valueSource()).isEqualTo(ConfigValueSource.STORED);
        SystemConfigView deployPropertyView = InMemorySystemConfigStore.findView(views, ConfigKey.SUMMARY_DAILY_LIMIT);
        assertThat(deployPropertyView.displayValue()).isEqualTo("150");
        assertThat(deployPropertyView.isStored()).isFalse();
        assertThat(deployPropertyView.valueSource()).isEqualTo(ConfigValueSource.DEPLOY_PROPERTY);
        assertThat(InMemorySystemConfigStore.findView(views, ConfigKey.SUMMARY_BATCH_SIZE).valueSource()).isEqualTo(ConfigValueSource.CODE_DEFAULT);
    }

    @Test
    void withoutMasterKeyRefusesToStoreSecretAndReadsNothing() {
        service.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, ACTOR, null);
        SystemConfigService serviceWithoutMasterKey = store.createService(new SecretCipher(""));

        assertThat(serviceWithoutMasterKey.canStoreSecret()).isFalse();
        assertThatThrownBy(() -> serviceWithoutMasterKey.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE + " ", ACTOR, null))
                .isInstanceOf(CustomException.class)
                .hasMessageNotContaining("sk-test")
                .extracting("errorCode").isEqualTo(ErrorCode.SECRET_MASTER_KEY_MISSING);
        // 저장된 암호문은 그대로, 읽기는 없음으로
        assertThat(store.storedConfigs).hasSize(1);
        assertThat(store.storedConfigs.get(0).getConfigValue()).startsWith("enc:v1:");
        assertThat(serviceWithoutMasterKey.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEmpty();
        assertThat(InMemorySystemConfigStore.findView(serviceWithoutMasterKey.findViews(), ConfigKey.ANTHROPIC_API_KEYS).hasSecretValue()).isFalse();
        // 비밀이 아닌 설정은 그대로 바꿀 수 있음
        assertThat(serviceWithoutMasterKey.update(ConfigKey.AI_TITLE_BATCH_SIZE, "50", ACTOR, null)).isTrue();
    }

    @Test
    void secretEncryptedUnderAnotherKeyNameIsNotReadable() {
        // 다른 행의 암호문을 옮겨 붙인 경우 (AAD 불일치): 없음으로 처리
        String valueOfAnotherKey = new SecretCipher(InMemorySystemConfigStore.MASTER_KEY).encrypt("ANOTHER_SECRET_KEY", SECRET_VALUE);
        store.storedConfigs.add(SystemConfig.create(ConfigKey.ANTHROPIC_API_KEYS, valueOfAnotherKey));

        assertThat(service.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEmpty();
    }

    @Test
    void reportsSecretStoredUnderAnotherMasterKeyAsUnreadable() {
        // 마스터 키를 바꿔 띄운 서버: 값은 없음으로 읽히지만 "풀지 못함"으로 구분 (#87)
        service.update(ConfigKey.ANTHROPIC_API_KEYS, SECRET_VALUE, ACTOR, null);
        SystemConfigService serviceWithAnotherMasterKey = store.createService(new SecretCipher("another-master-key-0000000000"));

        assertThat(serviceWithAnotherMasterKey.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEmpty();
        assertThat(serviceWithAnotherMasterKey.hasUnreadableSecret(ConfigKey.ANTHROPIC_API_KEYS)).isTrue();
        assertThat(service.hasUnreadableSecret(ConfigKey.ANTHROPIC_API_KEYS)).isFalse();
        // 값 없음·마스터 키 없음은 풀지 못함이 아님 (마스터 키 없음은 canStoreSecret 으로 안내)
        assertThat(service.hasUnreadableSecret(ConfigKey.OPENAI_API_KEYS)).isFalse();
        assertThat(store.createService(new SecretCipher("")).hasUnreadableSecret(ConfigKey.ANTHROPIC_API_KEYS)).isFalse();
    }
}
