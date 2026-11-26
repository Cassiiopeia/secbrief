package kr.suhsaechan.secbrief.advisory.ai.calllog;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long>, JpaSpecificationExecutor<AiCallLog> {

    // 기간 합계: createdAt 이 from 이후인 기록 (오늘·이번 달 카드, 일일 예산 확인)
    @Query("select new kr.suhsaechan.secbrief.advisory.ai.calllog.AiCallSummary("
            + "count(l), sum(case when l.isSuccess = true then 1L else 0L end), "
            + "sum(cast(l.inputTokens as long)), sum(cast(l.outputTokens as long)), sum(l.estimatedCostUsd)) "
            + "from AiCallLog l where l.createdAt >= :from")
    AiCallSummary summarizeSince(@Param("from") Instant from);
}
