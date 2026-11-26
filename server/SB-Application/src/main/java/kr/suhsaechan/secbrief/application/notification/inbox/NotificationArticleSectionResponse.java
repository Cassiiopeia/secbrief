package kr.suhsaechan.secbrief.application.notification.inbox;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;
import kr.suhsaechan.secbrief.member.constant.AlertCategory;

/**
 * 긴급 알림 상세의 종류별 묶음: 알림 종류 1개 + 그 종류 기사 (알림 순서)
 */
public record NotificationArticleSectionResponse(AlertCategory alertCategory, List<ArticleResponse> articles) {
}
