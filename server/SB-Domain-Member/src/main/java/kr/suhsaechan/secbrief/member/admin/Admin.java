package kr.suhsaechan.secbrief.member.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.suhsaechan.secbrief.common.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 계정 1개: 아이디, 비밀번호 BCrypt 값 (평문은 저장하지 않음)
 */
@Entity
@Table(name = "admin")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin extends BaseEntity {

    // admin.username 열 길이 (V0.6.0 마이그레이션과 같은 값)
    public static final int MAXIMUM_USERNAME_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAXIMUM_USERNAME_LENGTH)
    private String username;

    // BCrypt 로 바꾼 값 (60자)
    @Column(name = "password", nullable = false, length = 100)
    private String passwordHash;

    public static Admin create(String username, String passwordHash) {
        Admin admin = new Admin();
        admin.username = username;
        admin.passwordHash = passwordHash;
        return admin;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
