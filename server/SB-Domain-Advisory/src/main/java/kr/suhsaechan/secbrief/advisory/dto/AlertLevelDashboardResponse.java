package kr.suhsaechan.secbrief.advisory.dto;

import java.util.List;

/**
 * 경보단계 화면: 지금 국내 경보단계(경보단계 글이 없으면 null), 변경 이력(최신순), 이번 주 위협 건수, 해외 지표, KISA 경보단계 게시판 주소
 */
public record AlertLevelDashboardResponse(
        AlertLevelStatusResponse current,
        List<AlertLevelChangeResponse> changes,
        WeeklyThreatCountsResponse weeklyCounts,
        List<InternationalIndicatorResponse> internationalIndicators,
        // 5단계 안내 표와 전체 공지가 있는 게시판 (원문 확인용)
        String sourceUrl
) {
}
