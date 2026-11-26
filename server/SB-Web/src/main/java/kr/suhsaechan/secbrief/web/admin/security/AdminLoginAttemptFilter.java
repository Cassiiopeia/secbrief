package kr.suhsaechan.secbrief.web.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.suhsaechan.secbrief.web.filter.RequestPaths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 잠긴 아이디의 로그인 요청 차단: 인증 처리보다 먼저 실행 (잠긴 동안은 비밀번호를 대조하지 않음)
 * 잠겼으면 로그인 화면(?locked)으로 되돌림, 그 외 요청은 그대로 통과
 */
@Slf4j
@RequiredArgsConstructor
public class AdminLoginAttemptFilter extends OncePerRequestFilter {

    private final AdminLoginAttemptLimiter adminLoginAttemptLimiter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean isLoginSubmit = HttpMethod.POST.matches(request.getMethod())
                && AdminSecurityPaths.LOGIN.equals(RequestPaths.withinApplication(request));
        return !isLoginSubmit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (adminLoginAttemptLimiter.isLocked(request.getParameter(AdminSecurityPaths.USERNAME_PARAMETER))) {
            log.warn("[admin login] rejected 302 : username is locked");
            response.sendRedirect(request.getContextPath() + AdminSecurityPaths.LOGIN_LOCKED_REDIRECT);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
