package kr.suhsaechan.secbrief.web.admin.controller;

import kr.suhsaechan.secbrief.application.admin.audit.AdminAuditQueryService;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLog;
import kr.suhsaechan.secbrief.web.admin.support.AdminPagination;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리자 기록 화면: 최신순 표 + 쪽 번호 (읽기 전용)
 */
@Controller
@RequiredArgsConstructor
public class AdminAuditController {

    private static final String AUDIT_VIEW = "admin/audit";

    private final AdminAuditQueryService adminAuditQueryService;

    @GetMapping("/admin/audit")
    public String showAuditLogs(@RequestParam(name = "page", defaultValue = "1") String pageParameter, Model model) {
        Page<AdminAuditLog> auditLogPage = adminAuditQueryService.findPage(parsePageNumber(pageParameter));
        model.addAttribute("auditLogs", auditLogPage.getContent());
        model.addAttribute("pagination", AdminPagination.of(auditLogPage));
        return AUDIT_VIEW;
    }

    // 숫자가 아니거나 너무 큰 값은 1쪽
    private static int parsePageNumber(String pageParameter) {
        try {
            return Integer.parseInt(pageParameter.trim());
        } catch (NumberFormatException exception) {
            return 1;
        }
    }
}
