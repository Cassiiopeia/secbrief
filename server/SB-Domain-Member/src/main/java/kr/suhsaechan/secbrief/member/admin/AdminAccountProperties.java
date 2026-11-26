package kr.suhsaechan.secbrief.member.admin;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 초기 관리자 계정 설정 (secbrief.admin.accounts[]): 운영 설정 파일에서만 채움
 */
@ConfigurationProperties(prefix = "secbrief.admin")
public record AdminAccountProperties(List<Account> accounts) {

    public AdminAccountProperties {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
    }

    /**
     * 초기 계정 1개: 아이디, 평문 비밀번호 (기동 시 BCrypt 로 바꿔 저장)
     */
    public record Account(String username, String password) {

        // 비밀번호가 로그·오류 문구에 찍히지 않게 가림
        @Override
        public String toString() {
            return "Account[username=" + username + ", password=<hidden>]";
        }
    }
}
