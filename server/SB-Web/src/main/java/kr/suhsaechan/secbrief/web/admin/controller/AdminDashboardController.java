package kr.suhsaechan.secbrief.web.admin.controller;

import kr.suhsaechan.secbrief.application.admin.dashboard.AdminDashboardService;
import java.util.List;
import kr.suhsaechan.secbrief.application.admin.source.AdminSourceRow;
import kr.suhsaechan.secbrief.application.admin.source.AdminSourceService;
import kr.suhsaechan.secbrief.web.admin.security.AdminSecurityPaths;
import kr.suhsaechan.secbrief.web.admin.support.AdminSourceHealth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 관리자 대시보드: 확인할 일, 숫자 카드, 바로 가기, 출처 상태 (추이 그래프는 화면이 /admin/stats/api/* 를 따로 부름)
 */
@Controller
@RequiredArgsConstructor
public class AdminDashboardController {

    private static final String DASHBOARD_VIEW = "admin/dashboard";

    private final AdminDashboardService adminDashboardService;
    private final AdminSourceService adminSourceService;

    @GetMapping(AdminSecurityPaths.DASHBOARD)
    public String showDashboard(Model model) {
        model.addAttribute("summary", adminDashboardService.loadSummary());
        List<AdminSourceRow> sourceRows = adminSourceService.findAllSources();
        model.addAttribute("sourceRows", sourceRows);
        model.addAttribute("sourceHealth", AdminSourceHealth.of(sourceRows));
        return DASHBOARD_VIEW;
    }
}
