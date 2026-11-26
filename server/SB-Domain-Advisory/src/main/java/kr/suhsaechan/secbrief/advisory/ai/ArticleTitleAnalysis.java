package kr.suhsaechan.secbrief.advisory.ai;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;

/**
 * AI 분석 결과: 주제 1개, 한국어 제목(국내 항목·번역 없음이면 null), 중요도, 제목에 나온 회사·제품 이름(없으면 null), 키워드(없으면 빈 목록),
 * 기술 분야 code (카탈로그 목록에서 고름, 없으면 null)
 * 번역·이름·키워드는 검증 전 값 (저장 단계에서 ArticleTitleAnalysisPolicy 가 거름)
 */
public record ArticleTitleAnalysis(ArticleTopic topic, String translatedTitle,
                                   ArticleImportance importance, String subjectName, List<String> keywords,
                                   String fieldCode) {

    /**
     * 분야 없는 결과
     */
    public ArticleTitleAnalysis(ArticleTopic topic, String translatedTitle,
                                ArticleImportance importance, String subjectName, List<String> keywords) {
        this(topic, translatedTitle, importance, subjectName, keywords, null);
    }

    /**
     * 키워드 없는 결과
     */
    public ArticleTitleAnalysis(ArticleTopic topic, String translatedTitle,
                                ArticleImportance importance, String subjectName) {
        this(topic, translatedTitle, importance, subjectName, List.of(), null);
    }
}
