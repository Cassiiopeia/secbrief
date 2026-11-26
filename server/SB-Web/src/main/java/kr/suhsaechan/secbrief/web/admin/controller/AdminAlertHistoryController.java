package kr.suhsaechan.secbrief.web.admin.controller;

import kr.suhsaechan.secbrief.application.notification.inbox.NotificationQueryService;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import kr.suhsaechan.secbrief.web.admin.support.AdminFlash;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 긴급 알림 기록: 최근 30일 발송 기록 (최신순, 다음 쪽은 커서), 앱 알림함과 같은 조회
 */
@Controller
@RequiredArgsConstructor
public class AdminAlertHistoryController {

    private static final String ALERTS_VIEW = "admin/alerts";
    private static final int PAGE_SIZE = NotificationQueryService.MAXIMUM_PAGE_SIZE;

    private final NotificationQueryService notificationQueryService;

    @GetMapping("/admin/alerts")
    public String showAlerts(@RequestParam(required = false) String cursor, Model model) {
        try {
            model.addAttribute("alertPage", notificationQueryService.findNotifications(NotificationKind.ALERT, cursor, PAGE_SIZE));
        } catch (CustomException exception) {
            // 깨진 커서: 첫 쪽으로 (안내는 띄움)
            model.addAttribute(AdminFlash.ERROR_ATTRIBUTE, AdminFlash.toErrorText(exception.getErrorCode()));
            model.addAttribute("alertPage", notificationQueryService.findNotifications(NotificationKind.ALERT, null, PAGE_SIZE));
        }
        model.addAttribute("isFirstPage", cursor == null || cursor.isBlank());
        return ALERTS_VIEW;
    }
}
