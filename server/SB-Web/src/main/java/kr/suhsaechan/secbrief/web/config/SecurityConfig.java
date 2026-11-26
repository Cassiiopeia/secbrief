package kr.suhsaechan.secbrief.web.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageCounter;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.common.exception.ErrorResponse;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.common.json.JsonSupport;
import kr.suhsaechan.secbrief.member.admin.AdminAccountService;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginAttemptFilter;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginAttemptLimiter;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginFailureHandler;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginSuccessHandler;
import kr.suhsaechan.secbrief.web.admin.security.AdminSecurityPaths;
import kr.suhsaechan.secbrief.web.filter.ApiUsageCountFilter;
import kr.suhsaechan.secbrief.web.share.SharePaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 보안 체인 2개: 관리자(/admin/**, 세션 폼 로그인, CSRF 켬) > 공개 API(/api/**, 공유 링크 경로, 인증 없음, CSRF 끔, 세션 없음)
 * 그 밖의 경로는 공개 API 체인이 거부 (403)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 로그인 없이 여는 공개 경로 (앱이 부르는 API, 배포 헬스 체크)
    private static final String[] PUBLIC_API_MATCHERS = {"/api/**", "/actuator/health", "/actuator/health/**"};

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, UserDetailsService adminUserDetailsService,
                                                        AdminLoginAttemptLimiter adminLoginAttemptLimiter,
                                                        AdminAuditService adminAuditService,
                                                        AdminAccountService adminAccountService) throws Exception {
        http
                .securityMatcher(AdminSecurityPaths.ADMIN_MATCHER)
                .userDetailsService(adminUserDetailsService)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(AdminSecurityPaths.LOGIN).permitAll()
                        .requestMatchers(AdminSecurityPaths.STATIC_MATCHERS).permitAll()
                        .anyRequest().hasRole(AdminSecurityPaths.ADMIN_ROLE))
                .formLogin(form -> form
                        .loginPage(AdminSecurityPaths.LOGIN)
                        // 로그인 폼이 같은 주소로 제출 (기본값 /login 은 관리자 체인 밖)
                        .loginProcessingUrl(AdminSecurityPaths.LOGIN)
                        .successHandler(new AdminLoginSuccessHandler(adminLoginAttemptLimiter, adminAuditService))
                        .failureHandler(new AdminLoginFailureHandler(adminLoginAttemptLimiter, adminAuditService,
                                adminAccountService)))
                .logout(logout -> logout
                        .logoutUrl(AdminSecurityPaths.LOGOUT)
                        .logoutSuccessUrl(AdminSecurityPaths.LOGOUT_REDIRECT))
                // 로그인 화면 이동을 상대 경로로 (기본값은 http://호스트 절대 주소라 HTTPS 프록시 뒤에서 http 로 나감)
                .exceptionHandling(handling -> handling.authenticationEntryPoint((request, response, exception) ->
                        response.sendRedirect(request.getContextPath() + AdminSecurityPaths.LOGIN)))
                // 잠긴 아이디는 인증 처리 전에 되돌림 (비밀번호를 대조하지 않음)
                .addFilterBefore(new AdminLoginAttemptFilter(adminLoginAttemptLimiter),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain publicApiSecurityFilterChain(HttpSecurity http, ApiUsageCounter apiUsageCounter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // 보안 기본 헤더(Cache-Control: no-store 등)를 붙이지 않음: 공개 API 응답 헤더를 보안 추가 전과 같게 유지
                .headers(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 오류 응답 전달(/error)까지 막으면 원래 상태 코드 대신 403 이 나감
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(PUBLIC_API_MATCHERS).permitAll()
                        // 공유 미리보기·앱 링크 연결 파일 (SharePaths 한 곳)
                        .requestMatchers(SharePaths.PUBLIC_MATCHERS).permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) -> writeAccessDenied(response))
                        .accessDeniedHandler((request, response, exception) -> writeAccessDenied(response)))
                // 필터를 빈으로 두면 서블릿 필터로 한 번 더 등록돼 관리자 경로까지 셈
                .addFilterBefore(new ApiUsageCountFilter(apiUsageCounter), AuthorizationFilter.class);
        return http.build();
    }

    // 공개하지 않은 경로: 표준 에러 응답 {errorCode, message} 403
    private static void writeAccessDenied(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.ACCESS_DENIED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JsonSupport.OBJECT_MAPPER.writeValueAsString(ErrorResponse.of(ErrorCode.ACCESS_DENIED)));
    }
}
