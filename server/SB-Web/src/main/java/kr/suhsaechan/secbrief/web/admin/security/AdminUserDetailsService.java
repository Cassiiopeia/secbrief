package kr.suhsaechan.secbrief.web.admin.security;

import kr.suhsaechan.secbrief.member.admin.Admin;
import kr.suhsaechan.secbrief.member.admin.AdminAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 관리자 로그인용 계정 조회: admin 표의 아이디 > ROLE_ADMIN 사용자
 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAccountService adminAccountService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminAccountService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("admin account not found"));
        return User.withUsername(admin.getUsername())
                .password(admin.getPasswordHash())
                .roles(AdminSecurityPaths.ADMIN_ROLE)
                .build();
    }
}
