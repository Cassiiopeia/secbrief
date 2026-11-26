package kr.suhsaechan.secbrief.web.support;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.suhsaechan.secbrief.member.admin.AdminAccountProperties;
import kr.suhsaechan.secbrief.member.admin.AdminAccountService;
import kr.suhsaechan.secbrief.member.admin.AdminRepository;
import org.springframework.boot.test.context.TestComponent;

/**
 * 테스트용 관리자 계정: 임의 아이디·비밀번호로 만들고, 만든 계정만 지움 (실제 비밀번호를 쓰지 않음)
 */
@TestComponent
public class AdminTestAccounts {

    private final AdminAccountService adminAccountService;
    private final AdminRepository adminRepository;
    private final List<String> createdUsernames = new ArrayList<>();

    public AdminTestAccounts(AdminAccountService adminAccountService, AdminRepository adminRepository) {
        this.adminAccountService = adminAccountService;
        this.adminRepository = adminRepository;
    }

    /**
     * @return 만든 계정 (아이디, 평문 비밀번호)
     */
    public AdminAccountProperties.Account create() {
        AdminAccountProperties.Account account = new AdminAccountProperties.Account(randomUsername(), randomPassword());
        adminAccountService.createMissingAccounts(List.of(account));
        createdUsernames.add(account.username());
        return account;
    }

    public void deleteCreated() {
        createdUsernames.forEach(username -> adminRepository.findByUsername(username).ifPresent(adminRepository::delete));
        createdUsernames.clear();
    }

    public static String randomUsername() {
        return "test-admin-" + UUID.randomUUID().toString().substring(0, 12);
    }

    // 규칙(10자 이상, 영문·숫자)을 지키는 임의 값
    public static String randomPassword() {
        return "pw1" + UUID.randomUUID();
    }
}
