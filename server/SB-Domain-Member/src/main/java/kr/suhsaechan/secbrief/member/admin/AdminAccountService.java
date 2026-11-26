package kr.suhsaechan.secbrief.member.admin;

import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정: 초기 계정 만들기, 로그인용 조회, 비밀번호 변경
 * 비밀번호는 BCrypt 값만 저장하고 평문은 로그·예외 문구에 넣지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    /** 설정의 초기 계정 중 없는 아이디만 생성 (이미 있으면 비밀번호를 건드리지 않음) > 새로 만든 계정 수 */
    @Transactional
    public int createMissingAccounts(List<AdminAccountProperties.Account> accounts) {
        int createdCount = 0;
        for (AdminAccountProperties.Account account : accounts) {
            if (!isUsableAccount(account)) {
                log.warn("[admin account] skipped 400 : username or password is blank or too long");
                continue;
            }
            String username = account.username().trim();
            if (adminRepository.existsByUsername(username)) {
                continue;
            }
            adminRepository.save(Admin.create(username, passwordEncoder.encode(account.password())));
            createdCount++;
            log.info("[admin account] created : username={}", username);
        }
        return createdCount;
    }

    @Transactional(readOnly = true)
    public Optional<Admin> findByUsername(String username) {
        return adminRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public boolean isRegisteredUsername(String username) {
        return username != null && adminRepository.existsByUsername(username);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyAccount() {
        return adminRepository.count() > 0;
    }

    /**
     * 비밀번호 변경: 현재 비밀번호 대조 > 새 비밀번호 규칙 > 저장
     */
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw new CustomException(ErrorCode.ADMIN_CURRENT_PASSWORD_MISMATCH);
        }
        if (!AdminPasswordPolicy.isAcceptable(newPassword)) {
            throw new CustomException(ErrorCode.ADMIN_PASSWORD_POLICY_VIOLATION);
        }
        if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
            throw new CustomException(ErrorCode.ADMIN_PASSWORD_UNCHANGED);
        }
        admin.changePasswordHash(passwordEncoder.encode(newPassword));
        log.info("[admin account] password changed : username={}", username);
    }

    private static boolean isUsableAccount(AdminAccountProperties.Account account) {
        return account != null
                && account.username() != null && !account.username().isBlank()
                && account.username().trim().length() <= Admin.MAXIMUM_USERNAME_LENGTH
                && account.password() != null && !account.password().isBlank();
    }
}
