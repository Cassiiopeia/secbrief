package kr.suhsaechan.secbrief.web.scheduler;

import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageFlushService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이용 집계 저장 틱: 1분마다 메모리 카운터를 api_usage_daily 에 더함 (실패한 칸은 다음 틱에 다시)
 * secbrief.usage.flush-enabled=false 면 빈을 만들지 않음 (테스트)
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "secbrief.usage.flush-enabled", havingValue = "true", matchIfMissing = true)
public class ApiUsageFlushScheduler {

    private final ApiUsageFlushService apiUsageFlushService;

    @Scheduled(fixedDelayString = "${secbrief.usage.flush-tick-milliseconds:60000}",
            initialDelayString = "${secbrief.usage.flush-tick-milliseconds:60000}")
    public void flushApiUsage() {
        apiUsageFlushService.flush();
    }
}
