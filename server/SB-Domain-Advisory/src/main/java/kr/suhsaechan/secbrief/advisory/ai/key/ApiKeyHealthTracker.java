package kr.suhsaechan.secbrief.advisory.ai.key;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import kr.suhsaechan.secbrief.advisory.ai.calllog.AiFailureKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 키별 상태 (메모리): 마지막 성공·실패, 실패 후 5분 동안 뒤로 미룸 (쿨다운)
 * 서버를 다시 띄우면 비워짐 (이력은 ai_call_log 의 키 별명으로 확인)
 */
@Component
@RequiredArgsConstructor
public class ApiKeyHealthTracker {

    // 실패한 키를 뒤로 미루는 시간
    public static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(5);

    private final Clock clock;
    private final Map<String, ApiKeyHealth> healthByKeyId = new ConcurrentHashMap<>();

    /** 성공: 쿨다운 해제 */
    public void recordSuccess(String keyId) {
        Instant now = clock.instant();
        healthByKeyId.compute(keyId, (id, previousHealth) -> previousHealth == null
                ? new ApiKeyHealth(now, null, null, null)
                : new ApiKeyHealth(now, previousHealth.lastFailureAt(), previousHealth.lastFailureKind(), null));
    }

    /** 다음 키로 넘긴 실패: 지금부터 5분 쿨다운 */
    public void recordFailure(String keyId, AiFailureKind failureKind) {
        Instant now = clock.instant();
        healthByKeyId.compute(keyId, (id, previousHealth) -> new ApiKeyHealth(
                previousHealth == null ? null : previousHealth.lastSuccessAt(), now, failureKind,
                now.plus(FAILURE_COOLDOWN)));
    }

    /** 쿨다운 중인지 (마지막 실패 후 5분 안, 그 뒤 성공 없음) */
    public boolean isCoolingDown(String keyId) {
        ApiKeyHealth health = healthByKeyId.get(keyId);
        return health != null && health.cooldownUntil() != null && clock.instant().isBefore(health.cooldownUntil());
    }

    public Optional<ApiKeyHealth> findHealth(String keyId) {
        return Optional.ofNullable(healthByKeyId.get(keyId));
    }

    /** 키 값을 바꾸거나 지운 키: 옛 값의 상태를 버림 */
    public void forget(String keyId) {
        healthByKeyId.remove(keyId);
    }
}
