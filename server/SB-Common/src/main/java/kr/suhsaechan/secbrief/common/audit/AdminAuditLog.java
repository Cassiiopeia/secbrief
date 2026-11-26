package kr.suhsaechan.secbrief.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 동작 기록 1건: 누가, 무엇을, 어떤 대상에 (비밀값은 detail 에 넣지 않음)
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    // admin_audit_log.detail 열 길이
    public static final int MAXIMUM_DETAIL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String adminUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AdminAuditAction action;

    @Column(length = 50)
    private String targetType;

    @Column(length = 100)
    private String targetId;

    @Column(length = MAXIMUM_DETAIL_LENGTH)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    public static AdminAuditLog create(String adminUsername, AdminAuditAction action, String targetId,
                                       String detail, Instant createdAt) {
        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.adminUsername = adminUsername;
        auditLog.action = action;
        auditLog.targetType = action.getTargetType();
        auditLog.targetId = targetId;
        auditLog.detail = detail;
        auditLog.createdAt = createdAt;
        return auditLog;
    }
}
