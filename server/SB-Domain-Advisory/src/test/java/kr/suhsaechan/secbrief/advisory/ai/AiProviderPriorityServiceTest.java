package kr.suhsaechan.secbrief.advisory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 제공자 우선순위 관리: 아는 제공자 목록·순서, 바꾸기(정리 > 아는 코드 확인 > 저장), 모르는 코드·빈 목록 거부
 */
class AiProviderPriorityServiceTest {

    private final SystemConfigService systemConfigService = mock(SystemConfigService.class);
    private final StructuredCompletionClient anthropicClient = createProviderClient("anthropic", true);
    private final StructuredCompletionClient ollamaClient = createProviderClient("ollama", false);
    private final AiProviderPriorityService service = new AiProviderPriorityService(
            List.of(anthropicClient, ollamaClient,
                    new SelectedStructuredCompletionClient(List.of(anthropicClient, ollamaClient), systemConfigService)),
            systemConfigService);

    @Test
    void listsKnownProvidersInPriorityOrderWithoutSelectingClientItself() {
        givenPriority("ollama,anthropic");

        assertThat(service.findProviderViews()).containsExactly(
                new AiProviderView("ollama", false, 1), new AiProviderView("anthropic", true, 2));
    }

    @Test
    void putsProvidersOutsidePriorityLast() {
        givenPriority("anthropic");

        assertThat(service.findPriorityCodes()).containsExactly("anthropic");
        assertThat(service.findProviderViews()).containsExactly(
                new AiProviderView("anthropic", true, 1), new AiProviderView("ollama", false, 0));
    }

    @Test
    void savesNormalizedPriority() {
        String savedValue = service.updatePriority(List.of(" Ollama ", "", "anthropic", "ollama"), "admin", null);

        assertThat(savedValue).isEqualTo("ollama,anthropic");
        verify(systemConfigService).update(ConfigKey.AI_PROVIDER_PRIORITY, "ollama,anthropic", "admin", null);
    }

    @Test
    void rejectsUnknownProviderAndEmptyPriority() {
        assertThatThrownBy(() -> service.updatePriority(List.of("anthropic", "openai"), "admin", null))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.AI_PROVIDER_UNKNOWN);
        assertThatThrownBy(() -> service.updatePriority(List.of(" ", ""), "admin", null))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFIG_VALUE_REQUIRED);
        assertThatThrownBy(() -> service.updatePriority(null, "admin", null))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFIG_VALUE_REQUIRED);
        verify(systemConfigService, never()).update(any(), any(), any(), any());
    }

    private void givenPriority(String priorityValue) {
        when(systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY)).thenReturn(priorityValue);
    }

    private static StructuredCompletionClient createProviderClient(String providerCode, boolean isAvailable) {
        StructuredCompletionClient providerClient = mock(StructuredCompletionClient.class);
        when(providerClient.getProviderCode()).thenReturn(providerCode);
        when(providerClient.isAvailable()).thenReturn(isAvailable);
        return providerClient;
    }
}
