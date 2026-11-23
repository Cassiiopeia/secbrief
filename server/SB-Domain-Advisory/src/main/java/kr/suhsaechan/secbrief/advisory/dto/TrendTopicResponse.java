package kr.suhsaechan.secbrief.advisory.dto;

import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;

/**
 * 주제 1개의 이번 주 건수 <-> 지난주 같은 경과 구간 건수, 증감률(지난주 0 이면 null)
 */
public record TrendTopicResponse(ArticleTopic topic, long weeklyCount, long previousWeekCount, Integer changePercent) {
}
