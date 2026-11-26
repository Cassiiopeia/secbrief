package kr.suhsaechan.secbrief.advisory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.ArticleSummaryState;

/**
 * 기사 상세(브리프) 응답: 기사 + AI 요약·핵심 정리·대응 안내 + 원문에 있는 사실 값 + 관련 기사
 * 요약이 없으면(입력 없음·대기·실패) summary null, keyPoints 빈 목록, isAiSummarized false: 화면은 제목·사실 값·원문 링크로 열림
 */
public record ArticleDetailResponse(
        ArticleResponse article,
        String summary,
        List<String> keyPoints,
        // 대응 안내: 입력에 적힌 조치만 (없으면 null)
        String actionGuide,
        // boolean 은 is 접두사 그대로 JSON 이름으로 (앱 모델과 같은 이름)
        @JsonProperty("isAiSummarized") boolean isAiSummarized,
        List<SourceFactResponse> facts,
        List<ArticleResponse> relatedItems
) {

    public static ArticleDetailResponse of(Article article, List<Article> relatedArticles) {
        ArticleSummaryState summaryState = article.getSummaryState();
        boolean isAiSummarized = summaryState.isSummarized();
        return new ArticleDetailResponse(
                ArticleResponse.from(article),
                isAiSummarized ? summaryState.getSummary() : null,
                isAiSummarized ? summaryState.getKeyPoints() : List.of(),
                isAiSummarized ? summaryState.getActionGuide() : null,
                isAiSummarized,
                article.getSourceFacts().stream().map(SourceFactResponse::from).toList(),
                relatedArticles.stream().map(ArticleResponse::from).toList());
    }
}
