package kr.suhsaechan.secbrief.advisory.dto;

import java.time.Instant;

/**
 * 이번 주(서울 월요일 00:00부터) 위협 건수 3개: CISA KEV 새 등재(악용 중), 긴급 소식, KISA 보안공지
 */
public record WeeklyThreatCountsResponse(
        Instant weekStart,
        // 출처 CISA KEV 이고 등재일이 이번 주 (앱 악용 중 목록과 같은 기준)
        long exploitedVulnerabilityCount,
        long urgentArticleCount,
        long kisaNoticeCount
) {
}
