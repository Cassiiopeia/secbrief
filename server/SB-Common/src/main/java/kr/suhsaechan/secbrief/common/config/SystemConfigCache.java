package kr.suhsaechan.secbrief.common.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 설정 저장소 메모리 캐시: 전체 행을 30초 동안 들고 있음 (틱·호출마다 DB 를 읽지 않게, 다른 인스턴스의 변경은 30초 안에 반영)
 * 다시 읽기에 실패하면 기존 값을 그대로 씀 (설정 조회 실패가 수집·AI 처리를 멈추지 않게)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemConfigCache {

    static final Duration TIME_TO_LIVE = Duration.ofSeconds(30);

    private final SystemConfigRepository systemConfigRepository;
    private final Clock clock;

    // 저장된 값 (빈 값·모르는 키 이름의 행은 들어 있지 않음)
    private volatile Map<ConfigKey, String> storedValueByKey = Map.of();
    // 마지막으로 읽은 시각 (null: 다음 조회 때 다시 읽음)
    private volatile Instant loadedAt;

    /** 저장된 값 (없으면 null, SECRET 은 암호문 그대로) */
    public String findStoredValue(ConfigKey key) {
        reloadIfStale();
        return storedValueByKey.get(key);
    }

    /** 다음 조회 때 다시 읽게 함 (값을 바꾼 직후) */
    public void invalidate() {
        loadedAt = null;
    }

    private void reloadIfStale() {
        Instant lastLoadedAt = loadedAt;
        if (lastLoadedAt != null && clock.instant().isBefore(lastLoadedAt.plus(TIME_TO_LIVE))) {
            return;
        }
        reload();
    }

    // 실패해도 읽은 시각은 갱신 (장애 중 조회마다 DB 를 두드리지 않게)
    private synchronized void reload() {
        try {
            Map<ConfigKey, String> loadedValueByKey = new EnumMap<>(ConfigKey.class);
            for (SystemConfig systemConfig : systemConfigRepository.findAll()) {
                ConfigKey key = toConfigKey(systemConfig.getConfigKey());
                String configValue = systemConfig.getConfigValue();
                if (key != null && configValue != null && !configValue.isBlank()) {
                    loadedValueByKey.put(key, configValue);
                }
            }
            storedValueByKey = Map.copyOf(loadedValueByKey);
        } catch (RuntimeException exception) {
            log.warn("[system config] reload failed, keeping cached values : cause={}",
                    exception.getClass().getSimpleName());
        } finally {
            loadedAt = clock.instant();
        }
    }

    // 이 버전이 모르는 키 이름(다른 버전이 넣은 행)은 null
    private static ConfigKey toConfigKey(String configKeyName) {
        try {
            return ConfigKey.valueOf(configKeyName);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }
}
