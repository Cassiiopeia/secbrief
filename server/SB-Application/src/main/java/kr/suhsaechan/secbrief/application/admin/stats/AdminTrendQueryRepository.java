package kr.suhsaechan.secbrief.application.admin.stats;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 추이 집계 조회: 표마다 date_trunc 1쿼리 (서울 날짜 기준, 기록 있는 칸만 반환 > 빈 칸은 서비스가 0 채움)
 * 기간: from 00:00 이상 ~ toExclusive 00:00 미만 (서울)
 */
@Repository
@RequiredArgsConstructor
public class AdminTrendQueryRepository {

    // 칸 첫날 표기 (AdminTrendChart.bucketStarts 와 같은 형식)
    private static final String BUCKET_FORMAT = "'YYYY-MM-DD'";
    private static final String SEOUL_ZONE = "'" + SeoulCalendar.SEOUL.getId() + "'";
    // 알림 기기 등록 추이의 줄 key (한 줄뿐)
    public static final String PUSH_DEVICE_SERIES_KEY = "REGISTERED";

    // article.collected_at (timestamptz) > 서울 날짜 칸, 종류별 건수
    private static final String COLLECTED_ARTICLE_SQL = """
            select to_char(date_trunc(:unit, collected_at at time zone %s), %s) as bucket_start,
                   kind as series_key, count(*) as total_count
            from article
            where collected_at >= :from and collected_at < :toExclusive
            group by 1, 2
            """.formatted(SEOUL_ZONE, BUCKET_FORMAT);

    // ai_call_log.created_at (timestamptz) > 서울 날짜 칸, 성공·실패 수와 추정 비용 합
    private static final String AI_CALL_SQL = """
            select to_char(date_trunc(:unit, created_at at time zone %s), %s) as bucket_start,
                   count(*) filter (where is_success) as success_count,
                   count(*) filter (where not is_success) as failure_count,
                   coalesce(sum(estimated_cost_usd), 0) as estimated_cost_usd
            from ai_call_log
            where created_at >= :from and created_at < :toExclusive
            group by 1
            """.formatted(SEOUL_ZONE, BUCKET_FORMAT);

    // api_usage_daily.usage_date (서울 날짜) > 칸, 묶음별 요청 수 합
    private static final String API_USAGE_SQL = """
            select to_char(date_trunc(:unit, usage_date::timestamp), %s) as bucket_start,
                   usage_group as series_key, sum(request_count) as total_count
            from api_usage_daily
            where usage_date >= :from and usage_date < :toExclusive
            group by 1, 2
            """.formatted(BUCKET_FORMAT);

    // push_device.created_at (timestamp, 서버 시각 = 서울) > 칸, 등록 건수
    private static final String PUSH_DEVICE_SQL = """
            select to_char(date_trunc(:unit, created_at), %s) as bucket_start, count(*) as total_count
            from push_device
            where created_at >= :from and created_at < :toExclusive
            group by 1
            """.formatted(BUCKET_FORMAT);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<AdminTrendBucketCount> countCollectedArticlesByKind(AdminTrendUnit unit, LocalDate from, LocalDate toExclusive) {
        return namedParameterJdbcTemplate.query(COLLECTED_ARTICLE_SQL, toInstantParameters(unit, from, toExclusive),
                (resultSet, rowNumber) -> new AdminTrendBucketCount(resultSet.getString("bucket_start"),
                        resultSet.getString("series_key"), resultSet.getLong("total_count")));
    }

    public List<AdminAiUsageBucket> sumAiCalls(AdminTrendUnit unit, LocalDate from, LocalDate toExclusive) {
        return namedParameterJdbcTemplate.query(AI_CALL_SQL, toInstantParameters(unit, from, toExclusive),
                (resultSet, rowNumber) -> new AdminAiUsageBucket(resultSet.getString("bucket_start"),
                        resultSet.getLong("success_count"), resultSet.getLong("failure_count"),
                        resultSet.getBigDecimal("estimated_cost_usd")));
    }

    public List<AdminTrendBucketCount> sumApiUsageByGroup(AdminTrendUnit unit, LocalDate from, LocalDate toExclusive) {
        return namedParameterJdbcTemplate.query(API_USAGE_SQL,
                Map.of("unit", unit.getSqlName(), "from", from, "toExclusive", toExclusive),
                (resultSet, rowNumber) -> new AdminTrendBucketCount(resultSet.getString("bucket_start"),
                        resultSet.getString("series_key"), resultSet.getLong("total_count")));
    }

    public List<AdminTrendBucketCount> countNewPushDevices(AdminTrendUnit unit, LocalDate from, LocalDate toExclusive) {
        return namedParameterJdbcTemplate.query(PUSH_DEVICE_SQL,
                Map.of("unit", unit.getSqlName(), "from", from.atStartOfDay(), "toExclusive", toExclusive.atStartOfDay()),
                (resultSet, rowNumber) -> new AdminTrendBucketCount(resultSet.getString("bucket_start"),
                        PUSH_DEVICE_SERIES_KEY, resultSet.getLong("total_count")));
    }

    // 서울 날짜 경계 > 시각 (timestamptz 열 비교용)
    private static Map<String, Object> toInstantParameters(AdminTrendUnit unit, LocalDate from, LocalDate toExclusive) {
        return Map.of("unit", unit.getSqlName(),
                "from", from.atStartOfDay(SeoulCalendar.SEOUL).toOffsetDateTime(),
                "toExclusive", toExclusive.atStartOfDay(SeoulCalendar.SEOUL).toOffsetDateTime());
    }
}
