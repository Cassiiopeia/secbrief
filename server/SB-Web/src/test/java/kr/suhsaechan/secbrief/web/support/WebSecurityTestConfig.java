package kr.suhsaechan.secbrief.web.support;

import static org.mockito.Mockito.mock;

import java.time.Clock;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageCounter;
import kr.suhsaechan.secbrief.common.audit.AdminAuditService;
import kr.suhsaechan.secbrief.member.admin.AdminAccountService;
import kr.suhsaechan.secbrief.web.admin.security.AdminLoginAttemptLimiter;
import kr.suhsaechan.secbrief.web.config.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * @WebMvcTest 용 보안 설정: 실제 SecurityConfig(관리자·공개 API 체인) + 체인이 요구하는 빈의 대역
 * 슬라이스 테스트는 @Configuration 을 읽지 않아, 붙이지 않으면 Boot 기본 보안(전부 인증·CSRF)이 걸림
 * 쓰는 쪽: @WebMvcTest(XxxController.class) @Import(WebSecurityTestConfig.class)
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class WebSecurityTestConfig {

    // 슬라이스에는 관리자 계정이 없음 (로그인 시도는 항상 실패)
    @Bean
    UserDetailsService adminUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("no admin account in slice test");
        };
    }

    @Bean
    AdminLoginAttemptLimiter adminLoginAttemptLimiter() {
        return new AdminLoginAttemptLimiter(Clock.systemUTC());
    }

    @Bean
    AdminAuditService adminAuditService() {
        return mock(AdminAuditService.class);
    }

    @Bean
    AdminAccountService adminAccountService() {
        return mock(AdminAccountService.class);
    }

    @Bean
    ApiUsageCounter apiUsageCounter() {
        return new ApiUsageCounter(Clock.systemUTC());
    }
}
