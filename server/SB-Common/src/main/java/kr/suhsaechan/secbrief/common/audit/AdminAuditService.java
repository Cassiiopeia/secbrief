package kr.suhsaechan.secbrief.common.audit;

import java.time.Clock;
import kr.suhsaechan.secbrief.common.text.TextTruncation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 동작 기록: 본 작업과 다른 트랜잭션으로 저장 (기록 실패가 본 작업을 막지 않음)
 * 비밀값(키·비밀번호)은 detail 에 넣지 않음 (호출부 책임: 이름·별명·있음/없음만)
 */
@Slf4j
@Service
public class AdminAuditService {

    // 로그인 전 동작(잠금 등)에 쓰는 이름
    public static final String UNKNOWN_ADMIN_USERNAME = "unknown";
    // admin_audit_log.admin_username · target_id 열 길이
    private static final int MAXIMUM_USERNAME_LENGTH = 50;
    private static final int MAXIMUM_TARGET_ID_LENGTH = 100;

    private final AdminAuditLogRepository adminAuditLogRepository;
    private final Clock clock;
    // 본 작업과 다른 트랜잭션 (REQUIRES_NEW)
    private final TransactionTemplate newTransactionTemplate;

    public AdminAuditService(AdminAuditLogRepository adminAuditLogRepository, Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.clock = clock;
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 기록 1건 저장 (길이 넘는 값은 자름, 저장·커밋 실패는 경고 로그만: 트랜잭션 바깥에서 잡아 호출부로 새지 않게)
     */
    public void record(String adminUsername, AdminAuditAction action, String targetId, String detail) {
        AdminAuditLog auditLog = AdminAuditLog.create(
                truncate(adminUsername == null || adminUsername.isBlank() ? UNKNOWN_ADMIN_USERNAME : adminUsername,
                        MAXIMUM_USERNAME_LENGTH),
                action, truncate(targetId, MAXIMUM_TARGET_ID_LENGTH),
                truncate(detail, AdminAuditLog.MAXIMUM_DETAIL_LENGTH), clock.instant());
        try {
            newTransactionTemplate.executeWithoutResult(status -> adminAuditLogRepository.save(auditLog));
        } catch (RuntimeException exception) {
            log.warn("[admin audit] failed 500 : action={}, cause={}", action, exception.getClass().getSimpleName());
        }
    }

    // null 은 그대로 (대상 번호·상세는 선택값)
    static String truncate(String text, int maximumLength) {
        return text == null ? null : TextTruncation.truncateKeepingSurrogatePairs(text, maximumLength);
    }
}
