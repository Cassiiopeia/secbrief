package kr.suhsaechan.secbrief.advisory.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kr.suhsaechan.secbrief.advisory.entity.CollectRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectRunRepository extends JpaRepository<CollectRun, Long> {

    // 출처의 가장 최근 수집 시도 (주기 판정 기준)
    Optional<CollectRun> findTopBySourceIdOrderByStartedAtDesc(Long sourceId);

    // 끝나지 않은 시도 중 기준 시각 전에 시작한 것 (기동 시 중단 표시 대상)
    List<CollectRun> findByFinishedAtIsNullAndStartedAtBefore(Instant startedBefore);
}
