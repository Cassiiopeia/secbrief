package kr.suhsaechan.secbrief.application.admin.usage;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 일별 이용 집계 저장: 날짜·묶음 행에 건수 더하기 (없으면 만들고, 있으면 더함)
 */
@Repository
@RequiredArgsConstructor
public class ApiUsageDailyRepository {

    // 여러 서버가 같은 행에 더해도 건수가 합쳐지게 덮어쓰기 대신 더하기
    private static final String ADD_REQUEST_COUNT_SQL = """
            insert into api_usage_daily (usage_date, usage_group, request_count)
            values (:usageDate, :usageGroup, :delta)
            on conflict (usage_date, usage_group)
            do update set request_count = api_usage_daily.request_count + excluded.request_count
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public void addRequestCount(ApiUsageKey usageKey, long delta) {
        namedParameterJdbcTemplate.update(ADD_REQUEST_COUNT_SQL, Map.of(
                "usageDate", usageKey.usageDate(),
                "usageGroup", usageKey.usageGroup().name(),
                "delta", delta));
    }
}
