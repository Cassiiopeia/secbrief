package kr.suhsaechan.secbrief.web.controller;

import jakarta.validation.Valid;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import kr.suhsaechan.secbrief.member.dto.PushDeviceRegisterRequest;
import kr.suhsaechan.secbrief.member.dto.PushDeviceResponse;
import kr.suhsaechan.secbrief.member.service.PushDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 기기 API: 푸시 토큰과 켠 알림 종류 등록 (같은 토큰은 설정 교체, 빈 목록이면 삭제, 인증 없음)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/push-devices")
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    @PutMapping
    public ApiResponse<PushDeviceResponse> registerPushDevice(@Valid @RequestBody PushDeviceRegisterRequest request) {
        return ApiResponse.of(pushDeviceService.register(request));
    }
}
