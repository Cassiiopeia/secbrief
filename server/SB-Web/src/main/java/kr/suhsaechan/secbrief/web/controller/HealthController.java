package kr.suhsaechan.secbrief.web.controller;

import java.util.Map;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버 상태 확인: 배포 후 실측·앱 연결 확인용
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private static final String STATUS_KEY = "status";
    private static final String STATUS_UP = "UP";

    @GetMapping
    public ApiResponse<Map<String, String>> getHealth() {
        return ApiResponse.of(Map.of(STATUS_KEY, STATUS_UP));
    }
}
