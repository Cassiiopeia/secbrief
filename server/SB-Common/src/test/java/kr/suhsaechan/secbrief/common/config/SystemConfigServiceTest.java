package kr.suhsaechan.secbrief.common.config;

import static kr.suhsaechan.secbrief.common.config.InMemorySystemConfigStore.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * 설정 저장소 (메모리 가짜 저장소): 값 우선순위(설정 저장소 > 운영 설정 > 기본값), 검증, 이력, 기본값 복원, 30초 캐시
 */
class SystemConfigServiceTest {

    private static final String ACTOR = "admin";

    private final InMemorySystemConfigStore store = new InMemorySystemConfigStore();
    private final SystemConfigService service = store.createService();

    @Test
    void usesCodeDefaultWhenNothingIsStoredOrConfigured() {
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
        assertThat(service.getInt(ConfigKey.AI_REQUEST_BATCH_SIZE)).isEqualTo(10);
        assertThat(service.getInt(ConfigKey.SUMMARY_BATCH_SIZE)).isEqualTo(10);
        assertThat(service.getInt(ConfigKey.SUMMARY_DAILY_LIMIT)).isEqualTo(300);
        assertThat(service.getString(ConfigKey.AI_PROVIDER_PRIORITY)).isEqualTo("anthropic");
        assertThat(service.getDecimal(ConfigKey.AI_DAILY_BUDGET_USD)).isEqualByComparingTo("-1");
        assertThat(service.getDecimal(ConfigKey.PRICE_ANTHROPIC_INPUT_PER_1M)).isEqualByComparingTo("0.10");
        assertThat(service.getDecimal(ConfigKey.PRICE_ANTHROPIC_OUTPUT_PER_1M)).isEqualByComparingTo("0.50");
        assertThat(service.getBoolean(ConfigKey.URGENT_ALERT_ENABLED)).isTrue();
        assertThat(service.getSecret(ConfigKey.ANTHROPIC_API_KEYS)).isEmpty();
    }

    @Test
    void prefersStoredValueOverDeployPropertyOverCodeDefault() {
        store.environment.setProperty("secbrief.ai.batch-size", "35");
        store.environment.setProperty("secbrief.ai.provider", "ollama");
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(35);
        assertThat(service.getString(ConfigKey.AI_PROVIDER_PRIORITY)).isEqualTo("ollama");

        service.update(ConfigKey.AI_TITLE_BATCH_SIZE, "50", ACTOR, null);
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(50);

        // 기본값 복원: 저장된 행을 지우고 운영 설정 값으로 돌아감
        service.resetToDefault(ConfigKey.AI_TITLE_BATCH_SIZE, ACTOR, "되돌림");
        assertThat(store.storedConfigs).isEmpty();
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(35);
    }

    @Test
    void ignoresDeployPropertyThatBreaksTheRule() {
        store.environment.setProperty("secbrief.ai.batch-size", "0");
        store.environment.setProperty("secbrief.summary.daily-limit", "many");

        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
        assertThat(service.getInt(ConfigKey.SUMMARY_DAILY_LIMIT)).isEqualTo(300);
    }

    @Test
    void fallsBackToDefaultWhenStoredRowBreaksTheRule() {
        // 손으로 고친 행: 숫자가 아님
        store.storedConfigs.add(SystemConfig.create(ConfigKey.AI_TITLE_BATCH_SIZE, "many"));
        store.storedConfigs.add(SystemConfig.create(ConfigKey.URGENT_ALERT_ENABLED, "yes"));

        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
        assertThat(service.getBoolean(ConfigKey.URGENT_ALERT_ENABLED)).isTrue();
    }

    @Test
    void rejectsInvalidValueWithoutSavingOrHistory() {
        assertThatThrownBy(() -> service.update(ConfigKey.AI_TITLE_BATCH_SIZE, "0", ACTOR, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONFIG_VALUE_OUT_OF_RANGE);
        assertThatThrownBy(() -> service.update(ConfigKey.URGENT_ALERT_ENABLED, "", ACTOR, null))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFIG_VALUE_REQUIRED);

        assertThat(store.storedConfigs).isEmpty();
        assertThat(store.savedHistories).isEmpty();
    }

    @Test
    void recordsHistoryWithOldAndNewValueActorAndReason() {
        assertThat(service.update(ConfigKey.AI_TITLE_BATCH_SIZE, " 50 ", ACTOR, "  새 소식이 밀려서  ")).isTrue();
        assertThat(service.update(ConfigKey.AI_TITLE_BATCH_SIZE, "80", "", "r".repeat(600))).isTrue();

        assertThat(store.storedConfigs).hasSize(1);
        assertThat(store.storedConfigs.get(0).getConfigValue()).isEqualTo("80");
        assertThat(store.savedHistories).hasSize(2);
        SystemConfigHistory firstHistory = store.savedHistories.get(0);
        assertThat(firstHistory.getConfigKey()).isEqualTo("AI_TITLE_BATCH_SIZE");
        assertThat(firstHistory.getOldValue()).isEqualTo("20");
        assertThat(firstHistory.getNewValue()).isEqualTo("50");
        assertThat(firstHistory.getChangedBy()).isEqualTo(ACTOR);
        assertThat(firstHistory.getReason()).isEqualTo("새 소식이 밀려서");
        assertThat(firstHistory.getCreatedAt()).isEqualTo(NOW);
        SystemConfigHistory secondHistory = store.savedHistories.get(1);
        assertThat(secondHistory.getOldValue()).isEqualTo("50");
        // 변경자가 비면 system, 사유는 열 길이로 자름
        assertThat(secondHistory.getChangedBy()).isEqualTo("system");
        assertThat(secondHistory.getReason()).hasSize(500);
    }

    @Test
    void doesNothingWhenValueIsUnchanged() {
        assertThat(service.update(ConfigKey.AI_TITLE_BATCH_SIZE, "20", ACTOR, null)).isFalse();
        service.update(ConfigKey.AI_TITLE_BATCH_SIZE, "50", ACTOR, null);
        assertThat(service.update(ConfigKey.AI_TITLE_BATCH_SIZE, "50", ACTOR, null)).isFalse();
        assertThat(service.resetToDefault(ConfigKey.SUMMARY_BATCH_SIZE, ACTOR, null)).isFalse();

        assertThat(store.savedHistories).hasSize(1);
    }

    @Test
    void resetRecordsHistoryBackToDefault() {
        service.update(ConfigKey.URGENT_ALERT_ENABLED, "false", ACTOR, null);

        assertThat(service.resetToDefault(ConfigKey.URGENT_ALERT_ENABLED, ACTOR, "다시 켬")).isTrue();

        assertThat(service.getBoolean(ConfigKey.URGENT_ALERT_ENABLED)).isTrue();
        SystemConfigHistory resetHistory = store.savedHistories.get(1);
        assertThat(resetHistory.getOldValue()).isEqualTo("false");
        assertThat(resetHistory.getNewValue()).isEqualTo("true");
        assertThat(resetHistory.getReason()).isEqualTo("다시 켬");
    }

    @Test
    void cachesStoredValuesForThirtySeconds() {
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
        // 다른 인스턴스가 값을 바꿈 (이 인스턴스의 캐시는 모름)
        store.storedConfigs.add(SystemConfig.create(ConfigKey.AI_TITLE_BATCH_SIZE, "50"));

        store.clock.advance(Duration.ofSeconds(29));
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(20);
        verify(store.systemConfigRepository, times(1)).findAll();

        store.clock.advance(Duration.ofSeconds(1));
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(50);
        verify(store.systemConfigRepository, times(2)).findAll();
    }

    @Test
    void readsNewValueRightAfterUpdateWithoutWaitingForCacheExpiry() {
        assertThat(service.getInt(ConfigKey.SUMMARY_DAILY_LIMIT)).isEqualTo(300);

        service.update(ConfigKey.SUMMARY_DAILY_LIMIT, "120", ACTOR, null);

        assertThat(service.getInt(ConfigKey.SUMMARY_DAILY_LIMIT)).isEqualTo(120);
    }

    @Test
    void keepsCachedValuesWhenReloadFails() {
        service.update(ConfigKey.AI_TITLE_BATCH_SIZE, "50", ACTOR, null);
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(50);

        when(store.systemConfigRepository.findAll()).thenThrow(new DataAccessResourceFailureException("db down"));
        store.clock.advance(Duration.ofSeconds(31));

        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(50);
        // 장애 중에는 조회마다 다시 읽지 않음 (다음 30초 뒤에 다시)
        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(50);
        verify(store.systemConfigRepository, times(2)).findAll();
    }

    @Test
    void skipsRowsWithUnknownKeyNames() {
        SystemConfig unknownConfig = mock(SystemConfig.class);
        when(unknownConfig.getConfigKey()).thenReturn("KEY_FROM_NEWER_VERSION");
        when(unknownConfig.getConfigValue()).thenReturn("1");
        store.storedConfigs.add(unknownConfig);
        store.storedConfigs.add(SystemConfig.create(ConfigKey.AI_TITLE_BATCH_SIZE, "50"));

        assertThat(service.getInt(ConfigKey.AI_TITLE_BATCH_SIZE)).isEqualTo(50);
    }

    @Test
    void findsHistoriesOfOneKey() {
        service.findHistories(ConfigKey.AI_TITLE_BATCH_SIZE);

        verify(store.systemConfigHistoryRepository).findByConfigKeyOrderByIdDesc(eq("AI_TITLE_BATCH_SIZE"), any());
    }
}
