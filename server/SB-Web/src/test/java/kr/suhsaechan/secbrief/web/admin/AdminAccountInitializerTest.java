package kr.suhsaechan.secbrief.web.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.suhsaechan.secbrief.member.admin.Admin;
import kr.suhsaechan.secbrief.member.admin.AdminAccountInitializer;
import kr.suhsaechan.secbrief.member.admin.AdminAccountProperties;
import kr.suhsaechan.secbrief.member.admin.AdminAccountService;
import kr.suhsaechan.secbrief.member.admin.AdminRepository;
import kr.suhsaechan.secbrief.web.support.AdminTestAccounts;
import kr.suhsaechan.secbrief.web.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 초기 관리자 계정 (DB): 없는 아이디만 BCrypt 로 저장, 이미 있으면 비밀번호 유지, 설정이 비면 만들지 않음
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminAccountInitializerTest extends PostgresContainerTestSupport {

    @Autowired
    private AdminAccountService adminAccountService;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final String username = AdminTestAccounts.randomUsername();

    @AfterEach
    void deleteAccount() {
        adminRepository.findByUsername(username).ifPresent(adminRepository::delete);
    }

    @Test
    void createsMissingAccountWithBcryptHash() {
        String password = AdminTestAccounts.randomPassword();

        runInitializer(List.of(new AdminAccountProperties.Account(username, password)));

        Admin admin = adminRepository.findByUsername(username).orElseThrow();
        assertThat(admin.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, admin.getPasswordHash())).isTrue();
        assertThat(admin.getCreatedAt()).isNotNull();
    }

    @Test
    void keepsExistingAccountPassword() {
        String firstPassword = AdminTestAccounts.randomPassword();
        runInitializer(List.of(new AdminAccountProperties.Account(username, firstPassword)));
        String firstHash = adminRepository.findByUsername(username).orElseThrow().getPasswordHash();

        // 재기동: 설정의 비밀번호가 달라져도 이미 있는 계정은 그대로
        runInitializer(List.of(new AdminAccountProperties.Account(username, AdminTestAccounts.randomPassword())));

        Admin admin = adminRepository.findByUsername(username).orElseThrow();
        assertThat(admin.getPasswordHash()).isEqualTo(firstHash);
        assertThat(passwordEncoder.matches(firstPassword, admin.getPasswordHash())).isTrue();
    }

    @Test
    void createsNothingWhenAccountsAreEmpty() {
        long previousCount = adminRepository.count();

        runInitializer(List.of());

        assertThat(adminRepository.count()).isEqualTo(previousCount);
    }

    private void runInitializer(List<AdminAccountProperties.Account> accounts) {
        new AdminAccountInitializer(new AdminAccountProperties(accounts), adminAccountService).run(null);
    }
}
