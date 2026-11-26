package kr.suhsaechan.secbrief.advisory.ai.key;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;

/**
 * 관리자 화면용 키 1개: 우선순위(1부터), 별명, 가린 키 값, 켜짐, 운영 설정의 키인지, 쿨다운, 마지막 성공·실패 시각
 * 키 값 평문은 들어가지 않음
 */
public record AnthropicApiKeyView(String id, int priority, String nickname, String maskedApiKey, boolean isEnabled,
                                  boolean isFromDeployProperty, boolean isCoolingDown, Instant lastSuccessAt,
                                  Instant lastFailureAt, AiFailureKind lastFailureKind) {
}
