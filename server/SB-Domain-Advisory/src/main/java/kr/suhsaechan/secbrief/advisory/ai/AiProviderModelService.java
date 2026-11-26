package kr.suhsaechan.secbrief.advisory.ai;

import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;
import kr.suhsaechan.secbrief.common.config.SystemConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 제공자별 모델 이름: OpenAI·Google 은 설정 저장소(OPENAI_MODEL, GOOGLE_MODEL), Anthropic 은 운영 설정 secbrief.ai.model 로 고정
 * 호출마다 읽어 관리자 화면에서 바꾼 모델이 다음 호출부터 반영됨
 */
@Service
public class AiProviderModelService {

    private final SystemConfigService systemConfigService;
    private final String anthropicModel;

    public AiProviderModelService(SystemConfigService systemConfigService,
                                  @Value("${secbrief.ai.model:claude-haiku-5-5}") String anthropicModel) {
        this.systemConfigService = systemConfigService;
        this.anthropicModel = anthropicModel;
    }

    public String findModel(KeyedAiProvider provider) {
        return provider.hasFixedModel() ? anthropicModel : systemConfigService.getString(provider.getModelConfigKey());
    }
}
