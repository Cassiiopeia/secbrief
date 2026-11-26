package kr.suhsaechan.secbrief.advisory.ai;

import java.util.List;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 제공자 선택: secbrief.ai.provider 값과 코드가 같은 제공자 1개에 위임 (제목 분석·요약 공통)
 * 모르는 값이면 기동은 계속하고 AI 처리만 건너뜀 (경고 로그 1회)
 */
@Slf4j
@Primary
@Component
public class SelectedStructuredCompletionClient implements StructuredCompletionClient {

    // 로그에 남길 설정값 최대 길이
    private static final int MAXIMUM_LOGGED_PROVIDER_CODE_LENGTH = 30;

    private final String providerCode;
    // 설정값과 맞는 제공자 (없으면 null)
    private final StructuredCompletionClient selectedClient;

    // providerClients: 제공자 구현 전부 (스프링이 이 빈 자신은 목록에 넣지 않음)
    public SelectedStructuredCompletionClient(List<StructuredCompletionClient> providerClients,
                                              @Value("${secbrief.ai.provider:anthropic}") String configuredProviderCode) {
        this.providerCode = toLoggableProviderCode(configuredProviderCode);
        String trimmedProviderCode = configuredProviderCode == null ? "" : configuredProviderCode.strip();
        this.selectedClient = providerClients.stream()
                .filter(providerClient -> providerClient.getProviderCode().equalsIgnoreCase(trimmedProviderCode))
                .findFirst()
                .orElse(null);
        if (selectedClient == null) {
            log.warn("[article ai] unknown provider : provider={}, supported={}", providerCode,
                    providerClients.stream().map(StructuredCompletionClient::getProviderCode).toList());
        }
    }

    @Override
    public String getProviderCode() {
        return providerCode;
    }

    @Override
    public boolean isAvailable() {
        return selectedClient != null && selectedClient.isAvailable();
    }

    @Override
    public StructuredCompletion requestCompletion(StructuredCompletionRequest request) {
        if (selectedClient == null) {
            throw new CustomException(ErrorCode.AI_API_ERROR, "unknown provider");
        }
        return selectedClient.requestCompletion(request);
    }

    // 로그용 값 정리 (영문·숫자·-·_ 외 > ?, 30자 제한)
    private static String toLoggableProviderCode(String configuredProviderCode) {
        String sanitizedProviderCode = (configuredProviderCode == null ? "" : configuredProviderCode.strip())
                .replaceAll("[^A-Za-z0-9_-]", "?");
        return sanitizedProviderCode.length() > MAXIMUM_LOGGED_PROVIDER_CODE_LENGTH
                ? sanitizedProviderCode.substring(0, MAXIMUM_LOGGED_PROVIDER_CODE_LENGTH)
                : sanitizedProviderCode;
    }
}
