package kr.suhsaechan.secbrief.application.admin.audit;

import kr.suhsaechan.secbrief.common.audit.AdminAuditLog;
import kr.suhsaechan.secbrief.common.audit.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 기록 조회: 최신순, 한 쪽 30건
 */
@Service
@RequiredArgsConstructor
public class AdminAuditQueryService {

    // 한 쪽에 보여 줄 기록 수
    public static final int PAGE_SIZE = 30;

    private final AdminAuditLogRepository adminAuditLogRepository;

    /**
     * @param pageNumber 1부터 시작 (1보다 작으면 1쪽)
     */
    @Transactional(readOnly = true)
    public Page<AdminAuditLog> findPage(int pageNumber) {
        return adminAuditLogRepository.findAllByOrderByIdDesc(PageRequest.of(Math.max(pageNumber, 1) - 1, PAGE_SIZE));
    }
}
