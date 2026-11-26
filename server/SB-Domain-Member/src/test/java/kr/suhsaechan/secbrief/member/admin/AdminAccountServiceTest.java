package kr.suhsaechan.secbrief.member.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 관리자 계정: 초기 계정은 없는 아이디만 BCrypt 로 생성, 비밀번호 변경 규칙 (현재 값 대조, 규칙, 같은 값 거부)
 * 비밀번호는 테스트마다 임의 값
 */
class AdminAccountServiceTest {

    private static final String USERNAME = "operator";

    // 테스트 속도를 위해 최소 강도
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final AdminAccountService adminAccountService = new AdminAccountService(adminRepository, passwordEncoder);

    @Test
    void createsOnlyMissingAccountsWithBcryptHash() {
        String newAccountPassword = randomPassword();
        given(adminRepository.existsByUsername("existing")).willReturn(true);
        given(adminRepository.existsByUsername("fresh")).willReturn(false);

        int createdCount = adminAccountService.createMissingAccounts(List.of(
                new AdminAccountProperties.Account("existing", randomPassword()),
                new AdminAccountProperties.Account("fresh", newAccountPassword)));

        assertThat(createdCount).isEqualTo(1);
        ArgumentCaptor<Admin> savedAdmin = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(savedAdmin.capture());
        assertThat(savedAdmin.getValue().getUsername()).isEqualTo("fresh");
        assertThat(savedAdmin.getValue().getPasswordHash()).isNotEqualTo(newAccountPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(newAccountPassword, savedAdmin.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void skipsBlankOrTooLongAccounts() {
        int createdCount = adminAccountService.createMissingAccounts(List.of(
                new AdminAccountProperties.Account(" ", randomPassword()),
                new AdminAccountProperties.Account("no-password", ""),
                new AdminAccountProperties.Account("x".repeat(Admin.MAXIMUM_USERNAME_LENGTH + 1), randomPassword())));

        assertThat(createdCount).isZero();
        verify(adminRepository, never()).save(any());
    }

    @Test
    void hidesPasswordInAccountText() {
        String password = randomPassword();

        assertThat(new AdminAccountProperties.Account(USERNAME, password).toString()).doesNotContain(password);
    }

    @Test
    void changesPasswordWhenCurrentMatchesAndPolicyPasses() {
        String currentPassword = randomPassword();
        String newPassword = randomPassword();
        Admin admin = givenAdminWithPassword(currentPassword);

        adminAccountService.changePassword(USERNAME, currentPassword, newPassword);

        assertThat(passwordEncoder.matches(newPassword, admin.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(currentPassword, admin.getPasswordHash())).isFalse();
    }

    @Test
    void rejectsWrongCurrentPassword() {
        String currentPassword = randomPassword();
        Admin admin = givenAdminWithPassword(currentPassword);
        String previousHash = admin.getPasswordHash();

        assertThatThrownBy(() -> adminAccountService.changePassword(USERNAME, randomPassword(), randomPassword()))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_CURRENT_PASSWORD_MISMATCH));
        assertThat(admin.getPasswordHash()).isEqualTo(previousHash);
    }

    @Test
    void rejectsNewPasswordAgainstPolicy() {
        String currentPassword = randomPassword();
        givenAdminWithPassword(currentPassword);

        assertThatThrownBy(() -> adminAccountService.changePassword(USERNAME, currentPassword, "short1"))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_PASSWORD_POLICY_VIOLATION));
    }

    @Test
    void rejectsSamePasswordAsCurrent() {
        String currentPassword = randomPassword();
        givenAdminWithPassword(currentPassword);

        assertThatThrownBy(() -> adminAccountService.changePassword(USERNAME, currentPassword, currentPassword))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_PASSWORD_UNCHANGED));
    }

    private Admin givenAdminWithPassword(String password) {
        Admin admin = Admin.create(USERNAME, passwordEncoder.encode(password));
        given(adminRepository.findByUsername(USERNAME)).willReturn(Optional.of(admin));
        return admin;
    }

    // 규칙(10자 이상, 영문·숫자)을 지키는 임의 값
    private static String randomPassword() {
        return "pw1" + UUID.randomUUID();
    }
}
