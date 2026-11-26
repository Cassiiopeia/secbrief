package kr.suhsaechan.secbrief.member.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 초기 관리자 계정 생성: 설정에 있고 DB 에 없는 아이디만
 * 설정도 DB 도 비어 있으면 만들지 않음 (로그인 불가, 경고 로그 1회)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final AdminAccountProperties adminAccountProperties;
    private final AdminAccountService adminAccountService;

    @Override
    public void run(ApplicationArguments arguments) {
        if (adminAccountProperties.accounts().isEmpty()) {
            if (!adminAccountService.hasAnyAccount()) {
                log.warn("[admin account] no account : secbrief.admin.accounts is empty, admin login is unavailable");
            }
            return;
        }
        adminAccountService.createMissingAccounts(adminAccountProperties.accounts());
    }
}
