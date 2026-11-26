package kr.suhsaechan.secbrief.common.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import kr.suhsaechan.secbrief.common.crypto.SecretCipher;
import org.springframework.mock.env.MockEnvironment;

/**
 * 테스트용 설정 저장소 대역: 메모리 목록을 읽고 쓰는 가짜 저장소 + 운영 설정(MockEnvironment) + 멈춘 시계
 * DB 없이 SystemConfigService 를 만들어 값 우선순위·이력·캐시·비밀값 처리를 확인
 */
final class InMemorySystemConfigStore {

    static final Instant NOW = Instant.parse("2026-11-26T00:00:00Z");
    static final String MASTER_KEY = "test-master-key";

    final List<SystemConfig> storedConfigs = new ArrayList<>();
    final List<SystemConfigHistory> savedHistories = new ArrayList<>();
    final SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);
    final SystemConfigHistoryRepository systemConfigHistoryRepository = mock(SystemConfigHistoryRepository.class);
    final MockEnvironment environment = new MockEnvironment();
    final MutableClock clock = new MutableClock(NOW);

    InMemorySystemConfigStore() {
        when(systemConfigRepository.findAll()).thenAnswer(invocation -> List.copyOf(storedConfigs));
        when(systemConfigRepository.findByConfigKey(anyString())).thenAnswer(invocation -> storedConfigs.stream()
                .filter(systemConfig -> systemConfig.getConfigKey().equals(invocation.getArgument(0)))
                .findFirst());
        when(systemConfigRepository.save(any())).thenAnswer(invocation -> {
            storedConfigs.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        doAnswer(invocation -> storedConfigs.remove(invocation.<SystemConfig>getArgument(0)))
                .when(systemConfigRepository).delete(any());
        when(systemConfigHistoryRepository.save(any())).thenAnswer(invocation -> {
            savedHistories.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    /** 마스터 키가 있는 서비스 */
    SystemConfigService createService() {
        return createService(new SecretCipher(MASTER_KEY));
    }

    SystemConfigService createService(SecretCipher secretCipher) {
        return new SystemConfigService(systemConfigRepository, systemConfigHistoryRepository,
                new SystemConfigCache(systemConfigRepository, clock), secretCipher, environment, clock);
    }

    static SystemConfigView findView(List<SystemConfigView> views, ConfigKey key) {
        return views.stream().filter(view -> view.key() == key).findFirst().orElseThrow();
    }

    /** 테스트용 시계: advance 로만 흐름 (캐시 만료 확인) */
    static final class MutableClock extends Clock {

        private Instant currentInstant;

        private MutableClock(Instant startInstant) {
            this.currentInstant = startInstant;
        }

        void advance(Duration duration) {
            currentInstant = currentInstant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }
    }
}
