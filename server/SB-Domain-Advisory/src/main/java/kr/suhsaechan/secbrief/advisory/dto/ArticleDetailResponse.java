package kr.suhsaechan.secbrief.advisory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.ArticleSummaryState;

/**
 * 기사 상세(브리프) 응답: 기사 + AI 브리핑 + 이전 구조(요약·핵심 정리·대응 안내) + 원문에 있는 사실 값 + 관련 기사
 * 브리핑이 있는 기사: brief + 이전 구조 3개(브리핑에서 옮긴 값, 이전 앱 빌드용), 이전 요약만 있는 기사: brief null + 이전 구조 3개
 * 요약이 없으면(입력 없음·대기·실패) brief·summary null, keyPoints 빈 목록, isAiSummarized false: 화면은 제목·사실 값·원문 링크로 열림
 * 대응 모아보기(#71)는 responseGuideSections (빈 구역은 빼고 화면 순서대로), relatedItems 는 이전 앱 빌드·모아보기가 빌 때를 위해 계속 채움
 */
public record ArticleDetailResponse(
        ArticleResponse article,
        // 브리핑 구조의 글 (없으면 null)
        ArticleBriefResponse brief,
        // 이전 구조 3개: 이전 앱 빌드가 읽음
        String summary,
        List<String> keyPoints,
        // 대응 안내: 입력에 적힌 조치만 (없으면 null)
        String actionGuide,
        // boolean 은 is 접두사 그대로 JSON 이름으로 (앱 모델과 같은 이름)
        @JsonProperty("isAiSummarized") boolean isAiSummarized,
        List<SourceFactResponse> facts,
        List<ArticleResponse> relatedItems,
        // 공식 대응 > 확인된 정보 > 관련 기사 (찾은 것이 없으면 빈 목록)
        List<ResponseGuideSectionResponse> responseGuideSections
) {

    public static ArticleDetailResponse of(Article article, List<Article> relatedArticles,
                                           List<ResponseGuideSectionResponse> responseGuideSections) {
        ArticleSummaryState summaryState = article.getSummaryState();
        boolean isAiSummarized = summaryState.isSummarized();
        return new ArticleDetailResponse(
                ArticleResponse.from(article),
                summaryState.hasBrief() ? ArticleBriefResponse.from(summaryState.getBrief()) : null,
                isAiSummarized ? summaryState.getSummary() : null,
                isAiSummarized ? summaryState.getKeyPoints() : List.of(),
                isAiSummarized ? summaryState.getActionGuide() : null,
                isAiSummarized,
                article.getSourceFacts().stream().map(SourceFactResponse::from).toList(),
                relatedArticles.stream().map(ArticleResponse::from).toList(),
                responseGuideSections);
    }
}
