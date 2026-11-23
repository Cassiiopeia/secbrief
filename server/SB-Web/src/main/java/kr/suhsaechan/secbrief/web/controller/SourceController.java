package kr.suhsaechan.secbrief.web.controller;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.dto.SourceStatusResponse;
import kr.suhsaechan.secbrief.advisory.service.SourceQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출처 상태 API: 켜진 출처와 마지막 수집 결과 (설정 탭 출처 목록, 인증 없음)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sources")
public class SourceController {

    private final SourceQueryService sourceQueryService;

    @GetMapping
    public ApiResponse<List<SourceStatusResponse>> getSources() {
        return ApiResponse.of(sourceQueryService.findEnabledSources());
    }
}
