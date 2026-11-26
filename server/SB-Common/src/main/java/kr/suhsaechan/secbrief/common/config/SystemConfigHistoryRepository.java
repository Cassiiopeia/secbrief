package kr.suhsaechan.secbrief.common.config;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemConfigHistoryRepository extends JpaRepository<SystemConfigHistory, Long> {

    // 키별 변경 이력: 최신순 limit 건
    List<SystemConfigHistory> findByConfigKeyOrderByIdDesc(String configKey, Limit limit);
}
