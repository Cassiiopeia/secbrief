package kr.suhsaechan.secbrief.advisory.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 오늘 화면: 서울 날짜, 긴급 1건(72시간 안, 없으면 null), 오늘·이번 주 건수, 동향 1위(없으면 null), 최신 목록
 */
public record TodayResponse(
        LocalDate date,
        ArticleResponse urgentItem,
        long todayCount,
        long weeklyCount,
        TrendTopicResponse topTrend,
        List<ArticleResponse> streamItems
) {
}
