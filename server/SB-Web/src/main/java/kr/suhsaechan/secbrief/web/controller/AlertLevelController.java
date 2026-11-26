package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.advisory.dto.AlertLevelDashboardResponse;
import kr.suhsaechan.secbrief.advisory.service.AlertLevelQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 경보단계 화면 API (인증 없음): 지금 국내 경보단계, 변경 이력, 이번 주 위협 건수, 해외 지표
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alert-level")
public class AlertLevelController {

    private final AlertLevelQueryService alertLevelQueryService;

    @GetMapping
    public ApiResponse<AlertLevelDashboardResponse> getAlertLevelDashboard() {
        return ApiResponse.of(alertLevelQueryService.findDashboard());
    }
}
