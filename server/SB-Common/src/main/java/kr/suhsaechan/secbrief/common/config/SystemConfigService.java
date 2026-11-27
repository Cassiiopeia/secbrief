package kr.suhsaechan.secbrief.common.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.common.config.SystemConfigView.ConfigValueSource;
import kr.suhsaechan.secbrief.common.crypto.SecretCipher;
import kr.suhsaechan.secbrief.common.crypto.SecretMask;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 설정 저장소: 값 읽기(설정 저장소 > 운영 설정 > ConfigKey 기본값), 바꾸기(검증 > 저장 > 이력), 기본값 복원, 화면용 목록
 * SECRET 은 암호화해 저장하고 화면·이력·로그에 값을 내보내지 않음 (이력은 있음·없음만)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    // 사람이 아니라 코드가 바꿀 때의 변경자
    public static final String SYSTEM_ACTOR = "system";
    // SECRET 이력에 값 대신 남기는 표시
    public static final String HISTORY_SECRET_PRESENT = "있음";
    public static final String HISTORY_SECRET_ABSENT = "없음";
    // 화면에 보여 줄 키별 이력 건수
    private static final int MAXIMUM_HISTORY_COUNT = 50;

    private final SystemConfigRepository systemConfigRepository;
    private final SystemConfigHistoryRepository systemConfigHistoryRepository;
    private final SystemConfigCache systemConfigCache;
    private final SecretCipher secretCipher;
    private final Environment environment;
    private final Clock clock;

    /** 지금 값: 저장된 값이 규칙에 맞으면 그 값, 아니면 기본값 (SECRET 은 빈 문자열: getSecret 을 씀) */
    public String getString(ConfigKey key) {
        if (key.isSecret()) {
            return "";
        }
        String storedValue = systemConfigCache.findStoredValue(key);
        if (storedValue != null) {
            ConfigValueCheck check = SystemConfigValueValidator.check(key, storedValue);
            if (check.isAccepted()) {
                return check.normalizedValue();
            }
            // 손으로 고친 행·옛 규칙으로 저장한 값: 기본값으로 동작
            log.warn("[system config] stored value rejected, default used : key={}, cause={}", key, check.rejectionCode());
        }
        return findDefaultValue(key);
    }

    public int getInt(ConfigKey key) {
        return Integer.parseInt(getString(key));
    }

    public BigDecimal getDecimal(ConfigKey key) {
        return new BigDecimal(getString(key));
    }

    public boolean getBoolean(ConfigKey key) {
        return Boolean.parseBoolean(getString(key));
    }

    /** 비밀값 평문 (없음·마스터 키 없음·복호화 실패는 빈 문자열: 호출부가 "없음"으로 다룸) */
    public String getSecret(ConfigKey key) {
        String storedValue = systemConfigCache.findStoredValue(key);
        if (storedValue == null) {
            return "";
        }
        String plainValue = secretCipher.decrypt(key.name(), storedValue);
        return plainValue == null ? "" : plainValue;
    }

    /** 저장된 비밀값이 있는데 풀지 못하는지 (마스터 키 불일치·변조, 마스터 키 없음은 canStoreSecret 으로 따로 안내) */
    public boolean hasUnreadableSecret(ConfigKey key) {
        String storedValue = systemConfigCache.findStoredValue(key);
        if (storedValue == null || storedValue.isBlank() || !secretCipher.isAvailable()) {
            return false;
        }
        return secretCipher.decrypt(key.name(), storedValue) == null;
    }

    /** 비밀값을 저장할 수 있는지 (마스터 키 설정 여부) */
    public boolean canStoreSecret() {
        return secretCipher.isAvailable();
    }

    /** 설정 저장소에 값이 없을 때의 값: 운영 설정 값이 있고 규칙에 맞으면 그 값, 아니면 ConfigKey 기본값 */
    public String findDefaultValue(ConfigKey key) {
        String propertyValue = findPropertyValue(key);
        return propertyValue != null ? propertyValue : key.getDefaultValue();
    }

    /** 관리자 화면용 전체 목록 (ConfigKey 선언 순서, SECRET 은 가린 값만) */
    public List<SystemConfigView> findViews() {
        return Arrays.stream(ConfigKey.values()).map(this::toView).toList();
    }

    /** 키별 변경 이력 (최신순 50건) */
    public List<SystemConfigHistory> findHistories(ConfigKey key) {
        return systemConfigHistoryRepository.findByConfigKeyOrderByIdDesc(key.name(), Limit.of(MAXIMUM_HISTORY_COUNT));
    }

    /**
     * 값 바꾸기: 검증 > 저장(SECRET 은 암호화) > 이력. 반환은 실제로 바뀌었는지
     * SECRET 의 빈 값은 "그대로 두기" (지우려면 resetToDefault), 지금 값과 같으면 아무것도 하지 않음
     */
    @Transactional
    public boolean update(ConfigKey key, String rawValue, String actor, String reason) {
        ConfigValueCheck check = SystemConfigValueValidator.check(key, rawValue);
        if (!check.isAccepted()) {
            throw new CustomException(check.rejectionCode(), "config key=" + key);
        }
        String newValue = check.normalizedValue();
        if (key.isSecret() && newValue.isEmpty()) {
            return false;
        }
        // 마스터 키 없이는 비밀값을 저장하지 않음 (평문 저장 금지)
        if (key.isSecret() && !secretCipher.isAvailable()) {
            throw new CustomException(ErrorCode.SECRET_MASTER_KEY_MISSING, "config key=" + key);
        }

        Optional<SystemConfig> storedConfig = systemConfigRepository.findByConfigKey(key.name());
        String oldValue = toCurrentValue(key, storedConfig);
        if (oldValue.equals(newValue)) {
            return false;
        }
        String valueToStore = key.isSecret() ? secretCipher.encrypt(key.name(), newValue) : newValue;
        storedConfig.ifPresentOrElse(
                systemConfig -> systemConfig.changeValue(valueToStore),
                () -> systemConfigRepository.save(SystemConfig.create(key, valueToStore)));
        recordHistory(key, oldValue, newValue, actor, reason);
        invalidateCache();
        log.info("[system config] updated : key={}, actor={}", key, toLoggableActor(actor));
        return true;
    }

    /**
     * 기본값 복원: 저장된 행 삭제 (이후 운영 설정 값 > ConfigKey 기본값으로 동작, SECRET 은 없음). 반환은 지운 행이 있었는지
     */
    @Transactional
    public boolean resetToDefault(ConfigKey key, String actor, String reason) {
        Optional<SystemConfig> storedConfig = systemConfigRepository.findByConfigKey(key.name());
        if (storedConfig.isEmpty()) {
            return false;
        }
        String oldValue = toCurrentValue(key, storedConfig);
        systemConfigRepository.delete(storedConfig.get());
        recordHistory(key, oldValue, key.isSecret() ? "" : findDefaultValue(key), actor, reason);
        invalidateCache();
        log.info("[system config] reset to default : key={}, actor={}", key, toLoggableActor(actor));
        return true;
    }

    private SystemConfigView toView(ConfigKey key) {
        boolean isStored = systemConfigCache.findStoredValue(key) != null;
        if (key.isSecret()) {
            String plainValue = getSecret(key);
            // 전용 화면에서 다루는 값(JSON 목록)은 끝자리도 보여 주지 않음
            String displayValue = key.isManagedOnDedicatedScreen() && !plainValue.isEmpty()
                    ? SecretMask.MASK
                    : SecretMask.mask(plainValue);
            return new SystemConfigView(key, displayValue, "", isStored, !plainValue.isEmpty(),
                    isStored ? ConfigValueSource.STORED : ConfigValueSource.CODE_DEFAULT);
        }
        ConfigValueSource valueSource = isStored
                ? ConfigValueSource.STORED
                : findPropertyValue(key) != null ? ConfigValueSource.DEPLOY_PROPERTY : ConfigValueSource.CODE_DEFAULT;
        return new SystemConfigView(key, getString(key), findDefaultValue(key), isStored, false, valueSource);
    }

    // 운영 설정 값 (키가 묶여 있지 않거나, 비었거나, 규칙에 어긋나면 null)
    private String findPropertyValue(ConfigKey key) {
        if (key.getPropertyName() == null) {
            return null;
        }
        String propertyValue;
        try {
            propertyValue = environment.getProperty(key.getPropertyName());
        } catch (IllegalArgumentException exception) {
            // 풀 수 없는 자리표시자 (운영 설정 문제)
            return null;
        }
        if (propertyValue == null || propertyValue.isBlank()) {
            return null;
        }
        ConfigValueCheck check = SystemConfigValueValidator.check(key, propertyValue);
        return check.isAccepted() ? check.normalizedValue() : null;
    }

    // 사람이 보는 지금 값: 저장된 값(SECRET 은 평문) 또는 기본값. 풀 수 없는 비밀값은 없음으로 봄 (새 값으로 덮어쓸 수 있게)
    private String toCurrentValue(ConfigKey key, Optional<SystemConfig> storedConfig) {
        String storedValue = storedConfig.map(SystemConfig::getConfigValue).orElse(null);
        if (key.isSecret()) {
            String plainValue = storedValue == null || storedValue.isBlank()
                    ? null
                    : secretCipher.decrypt(key.name(), storedValue);
            return plainValue == null ? "" : plainValue;
        }
        return storedValue == null || storedValue.isBlank() ? findDefaultValue(key) : storedValue;
    }

    private void recordHistory(ConfigKey key, String oldValue, String newValue, String actor, String reason) {
        systemConfigHistoryRepository.save(SystemConfigHistory.create(key,
                key.isSecret() ? toSecretPresence(oldValue) : oldValue,
                key.isSecret() ? toSecretPresence(newValue) : newValue,
                truncate(actor == null || actor.isBlank() ? SYSTEM_ACTOR : actor.strip(),
                        SystemConfigHistory.MAXIMUM_CHANGED_BY_LENGTH),
                reason == null || reason.isBlank()
                        ? null
                        : truncate(reason.strip(), SystemConfigHistory.MAXIMUM_REASON_LENGTH),
                clock.instant()));
    }

    // 지금 트랜잭션 안의 조회와, 끝난 뒤 다른 스레드의 조회가 모두 새 값을 읽게 두 번 비움
    private void invalidateCache() {
        systemConfigCache.invalidate();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    systemConfigCache.invalidate();
                }
            });
        }
    }

    private static String toSecretPresence(String plainValue) {
        return plainValue == null || plainValue.isEmpty() ? HISTORY_SECRET_ABSENT : HISTORY_SECRET_PRESENT;
    }

    // 로그용 값 정리 (영문·숫자·-·_·. 외 > ?, 50자 제한)
    private static String toLoggableActor(String actor) {
        String sanitizedActor = (actor == null || actor.isBlank() ? SYSTEM_ACTOR : actor.strip())
                .replaceAll("[^A-Za-z0-9_.-]", "?");
        return truncate(sanitizedActor, SystemConfigHistory.MAXIMUM_CHANGED_BY_LENGTH);
    }

    private static String truncate(String text, int maximumLength) {
        return text.length() <= maximumLength ? text : text.substring(0, maximumLength);
    }
}
