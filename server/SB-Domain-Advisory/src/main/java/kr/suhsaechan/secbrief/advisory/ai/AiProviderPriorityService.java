package kr.suhsaechan.secbrief.advisory.ai;

import java.util.List;
import kr.suhsaechan.secbrief.common.config.ConfigKey;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 제공자 우선순위 관리 (관리자 화면): 아는 제공자 목록·상태 보기, 우선순위 바꾸기 (아는 코드만, 1개 이상)
 */
@Service
public class AiProviderPriorityService {

    // 실제 제공자 구현 (우선순위로 고르는 SelectedStructuredCompletionClient 는 뺌)
    private final List<StructuredCompletionClient> providerClients;
    private final SystemConfigService systemConfigService;

    public AiProviderPriorityService(List<StructuredCompletionClient> structuredCompletionClients,
                                     SystemConfigService systemConfigService) {
        this.providerClients = structuredCompletionClients.stream()
                .filter(client -> !(client instanceof SelectedStructuredCompletionClient))
                .toList();
        this.systemConfigService = systemConfigService;
    }

    /** 지금 우선순위 (제공자 코드, 앞이 먼저) */
    public List<String> findPriorityCodes() {
        return AiProviderPriorityPolicy.toProviderCodes(systemConfigService.getString(ConfigKey.AI_PROVIDER_PRIORITY));
    }

    /** 아는 제공자 전부: 우선순위에 든 것 먼저(순서대로), 나머지는 뒤에 */
    public List<AiProviderView> findProviderViews() {
        List<String> priorityCodes = findPriorityCodes();
        return providerClients.stream()
                .map(providerClient -> new AiProviderView(providerClient.getProviderCode(), providerClient.isAvailable(),
                        priorityCodes.indexOf(providerClient.getProviderCode()) + 1))
                .sorted((first, second) -> Integer.compare(toSortOrder(first), toSortOrder(second)))
                .toList();
    }

    /** 우선순위 바꾸기: 정리(소문자·중복 제거) > 아는 코드인지 확인 > 저장. 반환은 저장한 값 */
    public String updatePriority(List<String> requestedProviderCodes, String actor, String reason) {
        List<String> providerCodes = AiProviderPriorityPolicy.toProviderCodes(
                requestedProviderCodes == null ? "" : String.join(AiProviderPriorityPolicy.DELIMITER, requestedProviderCodes));
        if (providerCodes.isEmpty()) {
            throw new CustomException(ErrorCode.CONFIG_VALUE_REQUIRED);
        }
        boolean hasUnknownCode = providerCodes.stream().anyMatch(providerCode -> providerClients.stream()
                .noneMatch(providerClient -> providerClient.getProviderCode().equals(providerCode)));
        if (hasUnknownCode) {
            throw new CustomException(ErrorCode.AI_PROVIDER_UNKNOWN);
        }
        String priorityValue = AiProviderPriorityPolicy.toPriorityValue(providerCodes);
        systemConfigService.update(ConfigKey.AI_PROVIDER_PRIORITY, priorityValue, actor, reason);
        return priorityValue;
    }

    // 우선순위에 없는 제공자는 맨 뒤
    private static int toSortOrder(AiProviderView providerView) {
        return providerView.isInPriority() ? providerView.priority() : Integer.MAX_VALUE;
    }
}
