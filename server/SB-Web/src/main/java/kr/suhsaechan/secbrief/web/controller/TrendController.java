package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.dto.WeeklyTrendResponse;
import kr.suhsaechan.secbrief.advisory.service.WeeklyTrendQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주간 동향 API (인증 없음)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/trends")
public class TrendController {

    private final WeeklyTrendQueryService weeklyTrendQueryService;

    @GetMapping("/weekly")
    public ApiResponse<WeeklyTrendResponse> getWeeklyTrend() {
        return ApiResponse.of(weeklyTrendQueryService.findWeeklyTrend());
    }
}
