package kr.suhsaechan.secbrief.advisory.ai.key;

import java.time.Instant;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;

/**
 * 키 1개의 상태 (메모리): 마지막 성공 시각, 마지막 실패 시각·종류, 뒤로 미루는 기한 (성공하면 null)
 */
public record ApiKeyHealth(Instant lastSuccessAt, Instant lastFailureAt, AiFailureKind lastFailureKind,
                           Instant cooldownUntil) {
}
