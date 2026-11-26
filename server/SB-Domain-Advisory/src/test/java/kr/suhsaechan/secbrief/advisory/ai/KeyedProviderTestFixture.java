package kr.suhsaechan.secbrief.advisory.ai;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallLogService;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKey;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyCallRunner;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyService;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyServiceRegistry;
import kr.suhsaechan.secbrief.advisory.ai.key.ApiKeyFallbackExecutor;
import kr.suhsaechan.secbrief.advisory.ai.key.ApiKeyHealthTracker;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.advisory.support.MutableClock;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;

/**
 * HTTP 제공자 테스트 공통 준비 (OpenAI·Google): 가짜 키 목록·호출 기록·설정 저장소 + 실제 키 순회(AiApiKeyCallRunner, ApiKeyFallbackExecutor)
 */
final class KeyedProviderTestFixture {

    final AiApiKeyService aiApiKeyService = mock(AiApiKeyService.class);
    // 호출 기록은 가짜 (DB 없이 무엇을 기록하려 했는지만 확인)
    final AiCallLogService aiCallLogService = mock(AiCallLogService.class);
    final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    final ApiKeyHealthTracker tracker = new ApiKeyHealthTracker(new MutableClock(Instant.parse("2026-11-26T00:00:00Z")));
    private final AiApiKeyServiceRegistry aiApiKeyServiceRegistry = mock(AiApiKeyServiceRegistry.class);

    KeyedProviderTestFixture(KeyedAiProvider provider) {
        when(aiApiKeyServiceRegistry.find(provider)).thenReturn(aiApiKeyService);
    }

    void givenCallableKeys(AiApiKey... keys) {
        when(aiApiKeyService.findCallableKeys()).thenReturn(List.of(keys));
        when(aiApiKeyService.hasCallableKey()).thenReturn(keys.length > 0);
    }

    AiApiKeyCallRunner createCallRunner() {
        return new AiApiKeyCallRunner(aiApiKeyServiceRegistry, new ApiKeyFallbackExecutor(tracker), aiCallLogService);
    }

    // Anthropic 고정 모델은 이 테스트들이 쓰지 않음
    AiProviderModelService createModelService() {
        return new AiProviderModelService(systemConfigService, "unused-anthropic-model");
    }
}
