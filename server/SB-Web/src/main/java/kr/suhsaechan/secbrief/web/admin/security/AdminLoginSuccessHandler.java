package kr.suhsaechan.secbrief.web.admin.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

/**
 * 관리자 로그인 성공: 실패 기록 지움 > 감사 기록 > 대시보드로 이동
 */
@Slf4j
public class AdminLoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AdminLoginAttemptLimiter adminLoginAttemptLimiter;
    private final AdminAuditService adminAuditService;

    public AdminLoginSuccessHandler(AdminLoginAttemptLimiter adminLoginAttemptLimiter, AdminAuditService adminAuditService) {
        super(AdminSecurityPaths.DASHBOARD);
        // 로그인 전에 열려던 주소와 상관없이 언제나 대시보드로
        setAlwaysUseDefaultTargetUrl(true);
        this.adminLoginAttemptLimiter = adminLoginAttemptLimiter;
        this.adminAuditService = adminAuditService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        adminLoginAttemptLimiter.clear(authentication.getName());
        adminAuditService.record(authentication.getName(), AdminAuditAction.LOGIN_SUCCESS, authentication.getName(), null);
        log.info("[admin login] succeeded : username={}", authentication.getName());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
