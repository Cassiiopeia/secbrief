package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.dto.TodayResponse;
import kr.suhsaechan.secbrief.advisory.service.TodayQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오늘 화면 API (인증 없음)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/today")
public class TodayController {

    private final TodayQueryService todayQueryService;

    @GetMapping
    public ApiResponse<TodayResponse> getToday() {
        return ApiResponse.of(todayQueryService.findToday());
    }
}
