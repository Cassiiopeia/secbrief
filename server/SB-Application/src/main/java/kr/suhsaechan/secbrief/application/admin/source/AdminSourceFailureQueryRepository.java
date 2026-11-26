package kr.suhsaechan.secbrief.application.admin.source;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.CollectStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 출처별 가장 최근 실패 기록 조회 (collect_run, 출처마다 1행)
 */
@Repository
@RequiredArgsConstructor
public class AdminSourceFailureQueryRepository {

    // idx_collect_run_source_started_at (source_id, started_at desc) 순서 그대로 출처마다 첫 실패 1행
    private static final String LATEST_FAILURE_SQL = """
            select distinct on (source_id) source_id, error_code, error_message, coalesce(finished_at, started_at) as failed_at
            from collect_run
            where status = :failedStatus
            order by source_id, started_at desc
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<AdminSourceFailure> findLatestFailures() {
        return namedParameterJdbcTemplate.query(LATEST_FAILURE_SQL,
                Map.of("failedStatus", CollectStatus.FAILED.name()),
                (resultSet, rowNumber) -> {
                    Timestamp failedAt = resultSet.getTimestamp("failed_at");
                    return new AdminSourceFailure(resultSet.getLong("source_id"), resultSet.getString("error_code"),
                            resultSet.getString("error_message"), failedAt == null ? null : failedAt.toInstant());
                });
    }
}
