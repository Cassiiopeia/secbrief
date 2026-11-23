package kr.suhsaechan.secbrief.advisory.repository;

import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.entity.CollectRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectRunRepository extends JpaRepository<CollectRun, Long> {

    // 출처의 가장 최근 수집 시도 (주기 판정 기준)
    Optional<CollectRun> findTopBySourceIdOrderByStartedAtDesc(Long sourceId);
}
