package kr.suhsaechan.secbrief.web.controller;

import kr.suhsaechan.secbrief.application.notification.inbox.NotificationDetailResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationLatestResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationPageResponse;
import kr.suhsaechan.secbrief.application.notification.inbox.NotificationQueryService;
import kr.suhsaechan.secbrief.common.response.ApiResponse;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림함 API 3개 (인증 없음): 종류별 목록 (kind 기본 ALERT, 커서 + 크기), 종류별 최근 번호, 상세
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    public ApiResponse<NotificationPageResponse> getNotifications(
            @RequestParam(defaultValue = "ALERT") NotificationKind kind,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.of(notificationQueryService.findNotifications(kind, cursor, size));
    }

    @GetMapping("/latest")
    public ApiResponse<NotificationLatestResponse> getLatestNotificationIds() {
        return ApiResponse.of(notificationQueryService.findLatestIds());
    }

    @GetMapping("/{notificationId:\\d+}")
    public ApiResponse<NotificationDetailResponse> getNotification(@PathVariable long notificationId) {
        return ApiResponse.of(notificationQueryService.findNotification(notificationId));
    }
}
