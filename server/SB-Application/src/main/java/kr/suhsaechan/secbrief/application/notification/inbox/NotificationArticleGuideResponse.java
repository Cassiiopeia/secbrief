package kr.suhsaechan.secbrief.application.notification.inbox;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.dto.ResponseGuideItemResponse;

/**
 * 긴급 알림 상세의 기사 1건 대응 안내 요약: 브리핑의 대응(없으면 빈 목록) + 대응 모아보기 구역별 건수 + 첫 공식 대응 1건
 * 전체 목록은 기사 상세의 대응 모아보기에서 봄 (알림 상세는 건수·첫 공식 대응만)
 */
public record NotificationArticleGuideResponse(
        Long articleId,
        List<String> actions,
        int officialCount,
        int factCount,
        int newsCount,
        // 공식 대응 첫 항목 (KISA 먼저, 없으면 null)
        ResponseGuideItemResponse leadOfficialItem
) {
}
