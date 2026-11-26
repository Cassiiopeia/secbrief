package kr.suhsaechan.secbrief.web.admin.controller;

import kr.suhsaechan.secbrief.web.admin.security.AdminSecurityPaths;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 관리자 로그인 화면 (제출·로그아웃은 보안 체인이 처리)
 */
@Controller
public class AdminLoginController {

    private static final String LOGIN_VIEW = "admin/login";

    @GetMapping(AdminSecurityPaths.LOGIN)
    public String showLoginPage(Authentication authentication) {
        boolean isLoggedIn = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return isLoggedIn ? "redirect:" + AdminSecurityPaths.DASHBOARD : LOGIN_VIEW;
    }
}
