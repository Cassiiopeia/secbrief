package kr.suhsaechan.secbrief.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    // 관리자 기록 화면: 최신순
    Page<AdminAuditLog> findAllByOrderByIdDesc(Pageable pageable);
}
