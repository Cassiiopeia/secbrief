package kr.suhsaechan.secbrief.application.admin.dashboard;

import java.time.Clock;
import kr.suhsaechan.secbrief.application.admin.stats.AdminStatsCache;
import org.springframework.stereotype.Service;

/**
 * 대시보드 숫자: 오늘·이번 주 수집, AI 대기·호출·비용, 알림 기기, 앱 요청 (60초 캐시)
 */
@Service
public class AdminDashboardService {

    private static final String SUMMARY_CACHE_KEY = "summary";

    private final AdminDashboardQueryRepository adminDashboardQueryRepository;
    private final Clock clock;
    private final AdminStatsCache<AdminDashboardSummary> summaryCache;

    public AdminDashboardService(AdminDashboardQueryRepository adminDashboardQueryRepository, Clock clock) {
        this.adminDashboardQueryRepository = adminDashboardQueryRepository;
        this.clock = clock;
        this.summaryCache = new AdminStatsCache<>(clock::millis);
    }

    public AdminDashboardSummary loadSummary() {
        return summaryCache.getOrLoad(SUMMARY_CACHE_KEY, () -> adminDashboardQueryRepository.loadSummary(clock.instant()));
    }
}
