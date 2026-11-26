package kr.suhsaechan.secbrief.application.notification.inbox;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.dto.ArticleResponse;

/**
 * 아침 요약 상세 (DIGEST 만): 기사 수, 꼭 볼 것(주요 기사), 분야별 묶음(건수 많은 순, 분야 없으면 other)
 */
public record NotificationDigestResponse(int articleCount, List<ArticleResponse> notableArticles,
                                         List<DigestFieldSectionResponse> fieldSections) {

    /** 분야 1개 묶음: 분야 code + 기사 (요약에 넣은 순서) */
    public record DigestFieldSectionResponse(String fieldCode, List<ArticleResponse> articles) {
    }
}
