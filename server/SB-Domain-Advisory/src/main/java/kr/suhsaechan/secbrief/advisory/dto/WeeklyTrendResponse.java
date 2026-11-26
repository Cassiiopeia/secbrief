package kr.suhsaechan.secbrief.advisory.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 주간 동향: 이번 주 시작일(월요일, 서울) + 주제 9종 (이번 주 건수 내림차순)
 */
public record WeeklyTrendResponse(LocalDate weekStartDate, List<TrendTopicResponse> topics) {
}
