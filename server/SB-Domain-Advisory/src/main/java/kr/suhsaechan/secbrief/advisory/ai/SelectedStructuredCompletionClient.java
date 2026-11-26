package kr.suhsaechan.secbrief.advisory.ai;

import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 제공자 선택: AI_PROVIDER_PRIORITY 순서대로 쓸 수 있는 제공자를 시도, 호출이 실패하면 다음 제공자 (제목 분석·요약 공통)
 * 우선순위 값은 호출마다 설정 저장소에서 읽음 (없으면 운영 설정 secbrief.ai.provider), 모르는 코드는 건너뜀 (기동은 계속)
 */
@Slf4j
@Primary
@Component
public class SelectedStructuredCompletionClient implements StructuredCompletionClient {

    // 제공자 구현 전부 (스프링이 이 빈 자신은 목록에 넣지 않음)
    private final List<StructuredCompletionClient> providerClients;
    private final SystemConfigService systemConfigService;
    // 모르는 코드 경고를 남긴 마지막 설정값 (같은 값으로 틱마다 경고하지 않게)
    private volatile String lastWarnedPriorityValue;

    public SelectedStructuredCompletionClient(List<StructuredCompletionClient> providerClients,
                                              SystemConfigService systemConfigService) {
        this.providerClients = providerClients;
        this.systemConfigService = systemConfigService;
    }

    /** 지금 우선순위 설정값 (로그용으로 정리한 값, 예: anthropic,ollama) */
    @Override
    public String getProviderCode() {
        return AiProviderPriorityPolicy.toLoggableValue(findPriorityValue());
    }

    @Override
    public boolean isAvailable() {
        return findPrioritizedClients().stream().anyMatch(StructuredCompletionClient::isAvailable);
    }

    /**
     * 우선순위 순으로 호출: 성공하면 그 응답, 호출 실패면 다음 제공자, 다 실패하면 마지막 실패를 던짐
     * 거부·잘림은 호출 성공이라 넘기지 않음
     */
    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        List<StructuredCompletionClient> prioritizedClients = findPrioritizedClients();
        if (prioritizedClients.isEmpty()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "unknown provider");
        }
        List<StructuredCompletionClient> availableClients =
                prioritizedClients.stream().filter(StructuredCompletionClient::isAvailable).toList();
        if (availableClients.isEmpty()) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "provider not configured");
        }

        RuntimeException lastFailure = null;
        for (int clientIndex = 0; clientIndex < availableClients.size(); clientIndex++) {
            StructuredCompletionClient providerClient = availableClients.get(clientIndex);
            try {
                return providerClient.requestCompletion(request);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                boolean hasNextProvider = clientIndex + 1 < availableClients.size();
                log.warn("[article ai] provider call failed, {} : provider={}, cause={}",
                        hasNextProvider ? "trying next provider" : "no more providers",
                        providerClient.getProviderCode(), exception.getClass().getSimpleName());
            }
        }
        throw lastFailure instanceof CustomException
                ? lastFailure
                : new CustomException(ErrorCode.AI_API_ERROR, lastFailure.getClass().getSimpleName());
    }

    // 우선순위 순 제공자 (설정값의 코드 중 아는 것만)
    private List<StructuredCompletionClient> findPrioritizedClients() {
        String priorityValue = findPriorityValue();
        List<String> providerCodes = AiProviderPriorityPolicy.toProviderCodes(priorityValue);
        List<StructuredCompletionClient> prioritizedClients = providerCodes.stream()
                .map(this::findProviderClient)
                .flatMap(Optional::stream)
                .toList();
        if (prioritizedClients.size() < providerCodes.size() && !priorityValue.equals(lastWarnedPriorityValue)) {
            lastWarnedPriorityValue = priorityValue;
            log.warn("[article ai] unknown provider : priority={}, supported={}",
                    AiProviderPriorityPolicy.toLoggableValue(priorityValue),
                    providerClients.stream().map(StructuredCompletionClient::getProviderCode).toList());
        }
        return prioritizedClients;
    }

    private Optional<StructuredCompletionClient> findProviderClient(String providerCode) {
        return providerClients.stream()
                .filter(providerClient -> providerClient.getProviderCode().equalsIgnoreCase(providerCode))
                .findFirst();
    }

    private String findPriorityValue() {
        String priorityValue = systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY);
        return priorityValue == null ? "" : priorityValue;
    }
}
