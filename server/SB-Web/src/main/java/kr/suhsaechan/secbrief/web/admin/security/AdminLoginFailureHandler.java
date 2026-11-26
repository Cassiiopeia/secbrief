package kr.suhsaechan.secbrief.web.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.suhsaechan.secbrief.common.audit.AdminAuditAction;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.member.admin.AdminAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * 관리자 로그인 실패: 실패 1회 기록 > 5회째면 잠금 감사 기록 + 잠금 안내, 아니면 오류 안내
 * 입력한 비밀번호는 로그·기록에 남기지 않음
 */
@Slf4j
@RequiredArgsConstructor
public class AdminLoginFailureHandler implements AuthenticationFailureHandler {

    private final AdminLoginAttemptLimiter adminLoginAttemptLimiter;
    private final AdminAuditService adminAuditService;
    private final AdminAccountService adminAccountService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String rawUsername = request.getParameter(AdminSecurityPaths.USERNAME_PARAMETER);
        String recordableUsername = AdminLoginUsernames.toRecordableUsername(rawUsername,
                rawUsername != null && adminAccountService.isRegisteredUsername(rawUsername.trim()));
        boolean isNowLocked = adminLoginAttemptLimiter.recordFailure(rawUsername);
        if (isNowLocked) {
            adminAuditService.record(AdminAuditService.UNKNOWN_ADMIN_USERNAME, AdminAuditAction.LOGIN_LOCKED,
                    recordableUsername, "failures " + AdminLoginAttemptLimiter.MAXIMUM_FAILURE_COUNT
                            + ", locked " + AdminLoginAttemptLimiter.LOCK_DURATION.toMinutes() + " minutes");
            log.warn("[admin login] locked 401 : too many failures, username={}", recordableUsername);
            response.sendRedirect(request.getContextPath() + AdminSecurityPaths.LOGIN_LOCKED_REDIRECT);
            return;
        }
        log.warn("[admin login] rejected 401 : bad credentials, username={}", recordableUsername);
        response.sendRedirect(request.getContextPath() + AdminSecurityPaths.LOGIN_ERROR_REDIRECT);
    }
}
