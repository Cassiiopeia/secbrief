package kr.suhsaechan.secbrief.web.admin.controller;

import java.time.Clock;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.application.admin.stats.AdminAiUsageMetric;
import kr.suhsaechan.secbrief.application.admin.stats.AdminTrendChart;
import kr.suhsaechan.secbrief.application.admin.stats.AdminTrendRange;
import kr.suhsaechan.secbrief.application.admin.stats.AdminTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 추이 그래프 JSON 4종: 수집, AI 사용, 이용, 알림 기기 등록
 * 기간 값: unit(day·month), from, to (yyyy-MM-dd, 틀린 값은 기본값: 일별 30일 · 월별 12달, 최대 366일)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/stats/api")
public class AdminStatsApiController {

    private final AdminTrendService adminTrendService;
    private final Clock clock;

    @GetMapping("/collect")
    public AdminTrendChart getCollectTrend(@RequestParam(required = false) String unit,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return adminTrendService.loadCollectTrend(toRange(unit, from, to));
    }

    // metric: count 호출 수(성공·실패), cost 추정 비용(USD)
    @GetMapping("/ai-usage")
    public AdminTrendChart getAiUsageTrend(@RequestParam(required = false) String unit,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) String metric) {
        return adminTrendService.loadAiUsageTrend(toRange(unit, from, to), AdminAiUsageMetric.fromParameter(metric));
    }

    @GetMapping("/api-usage")
    public AdminTrendChart getApiUsageTrend(@RequestParam(required = false) String unit,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to) {
        return adminTrendService.loadApiUsageTrend(toRange(unit, from, to));
    }

    @GetMapping("/push-devices")
    public AdminTrendChart getPushDeviceTrend(@RequestParam(required = false) String unit,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return adminTrendService.loadPushDeviceTrend(toRange(unit, from, to));
    }

    private AdminTrendRange toRange(String unit, String from, String to) {
        return AdminTrendRange.of(unit, from, to, SeoulCalendar.toSeoulDate(clock.instant()));
    }
}
