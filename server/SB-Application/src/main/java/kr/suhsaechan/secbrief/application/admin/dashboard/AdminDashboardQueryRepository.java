package kr.suhsaechan.secbrief.application.admin.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.AiStatus;
import kr.suhsaechan.secbrief.advisory.constant.SummaryStatus;
import kr.suhsaechan.secbrief.advisory.policy.SeoulCalendar;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageGroup;
import kr.suhsaechan.secbrief.member.constant.NotificationKind;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 대시보드 숫자 조회: 표 6개(article, ai_call_log, notification, push_device_held_push, push_device, api_usage_daily)의 건수를 한 번에
 */
@Repository
@RequiredArgsConstructor
public class AdminDashboardQueryRepository {

    private static final String SUMMARY_SQL = """
            select
                (select count(*) from article where collected_at >= :todayStart) as today_collected_count,
                (select count(*) from article where collected_at >= :weekStart) as week_collected_count,
                (select count(*) from article where ai_status = :pendingAiStatus) as pending_title_analysis_count,
                (select count(*) from article where summary_status = :pendingSummaryStatus) as pending_summary_count,
                (select count(*) from article where summary_status = :failedSummaryStatus) as failed_summary_count,
                (select count(*) from ai_call_log where created_at >= :todayStart) as today_ai_call_count,
                (select count(*) from ai_call_log where created_at >= :todayStart and not is_success) as today_ai_failure_count,
                (select coalesce(sum(estimated_cost_usd), 0) from ai_call_log where created_at >= :todayStart) as today_ai_cost_usd,
                (select count(*) from notification where kind = :alertKind and pushed_at >= :todayStart) as today_pushed_alert_count,
                (select count(*) from notification where kind = :noticeKind and pushed_at >= :todayStart) as today_pushed_notice_count,
                (select count(*) from push_device_held_push) as held_push_count,
                (select count(*) from notification where kind = :noticeKind) as notice_count,
                (select count(*) from push_device) as push_device_count,
                (select coalesce(sum(request_count), 0) from api_usage_daily
                    where usage_date = :today and usage_group <> :imageGroup) as today_screen_request_count,
                (select coalesce(sum(request_count), 0) from api_usage_daily
                    where usage_date = :today and usage_group = :imageGroup) as today_image_request_count
            """;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public AdminDashboardSummary loadSummary(Instant now) {
        LocalDate today = SeoulCalendar.toSeoulDate(now);
        Map<String, Object> parameters = Map.of(
                "todayStart", toOffsetDateTime(SeoulCalendar.startOfToday(now)),
                "weekStart", toOffsetDateTime(SeoulCalendar.startOfWeek(now)),
                "pendingAiStatus", AiStatus.PENDING.name(),
                "pendingSummaryStatus", SummaryStatus.PENDING.name(),
                "failedSummaryStatus", SummaryStatus.FAILED.name(),
                "alertKind", NotificationKind.ALERT.name(),
                "noticeKind", NotificationKind.NOTICE.name(),
                "today", today,
                "imageGroup", ApiUsageGroup.ARTICLE_IMAGE.name());
        return namedParameterJdbcTemplate.queryForObject(SUMMARY_SQL, parameters,
                (resultSet, rowNumber) -> new AdminDashboardSummary(
                        resultSet.getLong("today_collected_count"),
                        resultSet.getLong("week_collected_count"),
                        resultSet.getLong("pending_title_analysis_count"),
                        resultSet.getLong("pending_summary_count"),
                        resultSet.getLong("failed_summary_count"),
                        resultSet.getLong("today_ai_call_count"),
                        resultSet.getLong("today_ai_failure_count"),
                        resultSet.getBigDecimal("today_ai_cost_usd"),
                        resultSet.getLong("today_pushed_alert_count"),
                        resultSet.getLong("today_pushed_notice_count"),
                        resultSet.getLong("held_push_count"),
                        resultSet.getLong("notice_count"),
                        resultSet.getLong("push_device_count"),
                        resultSet.getLong("today_screen_request_count"),
                        resultSet.getLong("today_image_request_count")));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(SeoulCalendar.SEOUL).toOffsetDateTime();
    }
}
