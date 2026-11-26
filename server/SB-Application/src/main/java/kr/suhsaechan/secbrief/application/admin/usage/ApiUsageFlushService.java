package kr.suhsaechan.secbrief.application.admin.usage;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 이용 건수 저장: 메모리 카운터 꺼내기 > 날짜·묶음별 더하기 > 실패한 칸은 카운터로 되돌림
 * 주기 실행(ApiUsageFlushScheduler) + 서버 종료 시 1회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiUsageFlushService {

    private final ApiUsageCounter apiUsageCounter;
    private final ApiUsageDailyRepository apiUsageDailyRepository;

    /**
     * @return 저장한 칸 수
     */
    public synchronized int flush() {
        int savedKeyCount = 0;
        for (Map.Entry<ApiUsageKey, Long> drained : apiUsageCounter.drain().entrySet()) {
            try {
                apiUsageDailyRepository.addRequestCount(drained.getKey(), drained.getValue());
                savedKeyCount++;
            } catch (RuntimeException exception) {
                apiUsageCounter.restore(drained.getKey(), drained.getValue());
                log.warn("[admin usage] failed 500 : save postponed, group={}, cause={}",
                        drained.getKey().usageGroup(), exception.getClass().getSimpleName());
            }
        }
        return savedKeyCount;
    }

    // 종료 직전 남은 건수 저장 (실패해도 종료는 계속)
    @PreDestroy
    void flushOnShutdown() {
        try {
            flush();
        } catch (RuntimeException exception) {
            log.warn("[admin usage] failed 500 : shutdown flush, cause={}", exception.getClass().getSimpleName());
        }
    }
}
