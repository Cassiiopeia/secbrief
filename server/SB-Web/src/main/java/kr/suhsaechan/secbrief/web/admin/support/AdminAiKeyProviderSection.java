package kr.suhsaechan.secbrief.web.admin.support;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.key.AiApiKeyView;
import kr.suhsaechan.secbrief.advisory.constant.KeyedAiProvider;

/**
 * AI 키 화면의 제공자 1개 구역: 제공자, 지금 모델, 등록한 키(가린 값·상태), 운영 설정의 키(읽기 전용),
 * 운영 설정의 키로 호출 중인지, 키를 더 추가할 수 있는지
 */
public record AdminAiKeyProviderSection(KeyedAiProvider provider, String model, List<AiApiKeyView> storedKeyViews,
                                        List<AiApiKeyView> deployPropertyKeyViews, boolean isUsingDeployPropertyKeys,
                                        boolean canAddKey) {
}
