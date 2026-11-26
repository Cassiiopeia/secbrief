package kr.suhsaechan.secbrief.application.admin.usage;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 이용 건수 메모리 카운터: 요청 경로에서는 더하기만 (DB 를 건드리지 않음), 저장은 ApiUsageFlushService 가 주기적으로
 * 날짜·묶음별 건수만 보관 (IP, User-Agent, 기기 식별값 없음)
 */
@Component
@RequiredArgsConstructor
public class ApiUsageCounter {

    private final Map<ApiUsageKey, LongAdder> pendingCountByKey = new ConcurrentHashMap<>();
    private final Clock clock;

    public void increment(ApiUsageGroup usageGroup) {
        ApiUsageKey usageKey = new ApiUsageKey(SeoulCalendar.toSeoulDate(clock.instant()), usageGroup);
        pendingCountByKey.computeIfAbsent(usageKey, key -> new LongAdder()).increment();
    }

    /**
     * 쌓인 건수를 꺼내고 0으로 되돌림 (0건인 칸은 빼고 반환, 지난 날짜의 빈 칸은 정리)
     */
    public Map<ApiUsageKey, Long> drain() {
        Map<ApiUsageKey, Long> drainedCountByKey = new HashMap<>();
        pendingCountByKey.forEach((usageKey, pendingCount) -> {
            long drainedCount = pendingCount.sumThenReset();
            if (drainedCount > 0) {
                drainedCountByKey.put(usageKey, drainedCount);
            }
        });
        // 꺼낸 뒤에도 비어 있는 지난 날짜 칸만 지움 (오늘 칸은 계속 씀)
        pendingCountByKey.entrySet().removeIf(entry ->
                entry.getKey().usageDate().isBefore(SeoulCalendar.toSeoulDate(clock.instant())) && entry.getValue().sum() == 0);
        return drainedCountByKey;
    }

    /**
     * 저장 실패한 건수 되돌리기 (다음 주기에 다시 저장)
     */
    public void restore(ApiUsageKey usageKey, long restoredCount) {
        pendingCountByKey.computeIfAbsent(usageKey, key -> new LongAdder()).add(restoredCount);
    }
}
