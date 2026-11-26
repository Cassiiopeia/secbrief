package kr.suhsaechan.secbrief.web.admin.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 관리자 로그인 실패 제한: 아이디별 15분 안에 5회 실패 > 15분 잠금 (메모리 보관, 재시작하면 풀림)
 * 없는 아이디도 똑같이 세어 잠금 여부로 계정 존재를 알 수 없게 함
 */
@Component
@RequiredArgsConstructor
public class AdminLoginAttemptLimiter {

    public static final int MAXIMUM_FAILURE_COUNT = 5;
    // 실패를 세는 구간이자 잠금 시간 (15분)
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    // 보관할 아이디 수 상한: 아무 아이디나 넣어 메모리를 채우지 못하게
    static final int MAXIMUM_TRACKED_USERNAME_COUNT = 10_000;
    // 아이디 열쇠 길이 상한 (admin.username 열 길이의 2배)
    private static final int MAXIMUM_USERNAME_KEY_LENGTH = 100;

    private final Map<String, AttemptState> attemptStateByUsername = new ConcurrentHashMap<>();
    private final Clock clock;

    public boolean isLocked(String username) {
        AttemptState attemptState = attemptStateByUsername.get(normalizeUsername(username));
        return attemptState != null && attemptState.isLockedAt(clock.instant());
    }

    /** 실패 1회 기록 > 이번 실패로 잠겼는지 */
    public boolean recordFailure(String username) {
        Instant now = clock.instant();
        String usernameKey = normalizeUsername(username);
        if (!attemptStateByUsername.containsKey(usernameKey)
                && attemptStateByUsername.size() >= MAXIMUM_TRACKED_USERNAME_COUNT) {
            removeFinishedStates(now);
        }
        AttemptState updatedState = attemptStateByUsername.compute(usernameKey, (key, previousState) ->
                previousState == null || previousState.isFinishedAt(now)
                        ? AttemptState.firstFailure(now)
                        : previousState.nextFailure(now));
        return updatedState.isLockedAt(now);
    }

    public void clear(String username) {
        attemptStateByUsername.remove(normalizeUsername(username));
    }

    // 대소문자·앞뒤 공백 변형으로 횟수를 피해 가지 못하게 통일
    static String normalizeUsername(String rawUsername) {
        if (rawUsername == null) {
            return "";
        }
        String normalizedUsername = rawUsername.trim().toLowerCase(Locale.ROOT);
        return normalizedUsername.length() > MAXIMUM_USERNAME_KEY_LENGTH
                ? normalizedUsername.substring(0, MAXIMUM_USERNAME_KEY_LENGTH)
                : normalizedUsername;
    }

    // 구간이 끝난 기록 정리 > 그래도 가득이면 잠기지 않은 기록부터 비움 (잠금은 유지)
    private void removeFinishedStates(Instant now) {
        attemptStateByUsername.values().removeIf(attemptState -> attemptState.isFinishedAt(now));
        if (attemptStateByUsername.size() >= MAXIMUM_TRACKED_USERNAME_COUNT) {
            attemptStateByUsername.values().removeIf(attemptState -> !attemptState.isLockedAt(now));
        }
    }

    /**
     * 아이디 1개의 실패 기록: 실패 횟수, 첫 실패 시각, 잠금 해제 시각 (잠기지 않았으면 null)
     */
    private record AttemptState(int failureCount, Instant firstFailedAt, Instant lockedUntil) {

        static AttemptState firstFailure(Instant now) {
            return new AttemptState(1, now, null);
        }

        AttemptState nextFailure(Instant now) {
            int nextFailureCount = failureCount + 1;
            return new AttemptState(nextFailureCount, firstFailedAt,
                    nextFailureCount >= MAXIMUM_FAILURE_COUNT ? now.plus(LOCK_DURATION) : null);
        }

        boolean isLockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }

        // 잠금이 풀렸거나, 잠기지 않은 채 세는 구간(15분)이 지남
        boolean isFinishedAt(Instant now) {
            return lockedUntil != null ? !now.isBefore(lockedUntil) : !now.isBefore(firstFailedAt.plus(LOCK_DURATION));
        }
    }
}
