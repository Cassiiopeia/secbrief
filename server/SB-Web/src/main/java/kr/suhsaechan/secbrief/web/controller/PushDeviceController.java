package kr.suhsaechan.secbrief.web.controller;

import jakarta.validation.Valid;
import kr.suhsaechan.secbrief.application.notification.settings.AlertEstimateRequest;
import kr.suhsaechan.secbrief.application.notification.settings.AlertEstimateResponse;
import kr.suhsaechan.secbrief.application.notification.settings.AlertEstimateService;
import kr.suhsaechan.secbrief.application.notification.settings.AlertSettingsService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.PushDeviceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 기기 API (인증 없음): 푸시 토큰·켠 종류·관심 기술·받는 방식 등록 (같은 토큰은 설정 교체, 다 비면 삭제), 어제 기준 알림 추정
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push-devices")
public class PushDeviceController {

    private final AlertSettingsService alertSettingsService;
    private final AlertEstimateService alertEstimateService;

    @PutMapping
    public ApiResponse<PushDeviceResponse> registerPushDevice(@Valid @RequestBody PushDeviceRegisterRequest request) {
        return ApiResponse.of(alertSettingsService.register(request));
    }

    /**
     * 어제 기준 알림 추정 (관심 기술 화면, 저장하지 않음)
     */
    @PostMapping("/alert-estimate")
    public ApiResponse<AlertEstimateResponse> estimateAlerts(@Valid @RequestBody AlertEstimateRequest request) {
        return ApiResponse.of(alertEstimateService.estimate(request));
    }
}
