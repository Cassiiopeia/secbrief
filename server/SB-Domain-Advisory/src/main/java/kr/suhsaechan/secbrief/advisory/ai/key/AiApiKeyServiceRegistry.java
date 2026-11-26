package kr.suhsaechan.secbrief.advisory.ai.key;

import java.util.EnumMap;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 제공자별 키 목록 관리 객체: 기동 시 제공자마다 1개씩 만들어 둠 (키 상태 추적기는 같이 씀)
 */
@Component
public class AiApiKeyServiceRegistry {

    private final Map<KeyedAiProvider, AiApiKeyService> keyServiceByProvider = new EnumMap<>(KeyedAiProvider.class);

    public AiApiKeyServiceRegistry(SystemConfigService systemConfigService, ApiKeyHealthTracker apiKeyHealthTracker,
                                   Environment environment) {
        for (KeyedAiProvider provider : KeyedAiProvider.values()) {
            keyServiceByProvider.put(provider, new AiApiKeyService(provider, systemConfigService, apiKeyHealthTracker,
                    findDeployPropertyApiKeys(provider, environment)));
        }
    }

    public AiApiKeyService find(KeyedAiProvider provider) {
        return keyServiceByProvider.get(provider);
    }

    // 운영 설정의 키 (쉼표로 여러 개, 운영 설정 키가 없는 제공자는 빈 문자열)
    private static String findDeployPropertyApiKeys(KeyedAiProvider provider, Environment environment) {
        return provider.getDeployPropertyName() == null
                ? ""
                : environment.getProperty(provider.getDeployPropertyName(), "");
    }
}
