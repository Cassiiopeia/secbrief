package kr.suhsaechan.secbrief.application.ai;

import java.util.List;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPolicy;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleEmbeddingRepository;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 결과 기록: 성공(번역·이름·키워드 검증 > 반영 > 키워드 교체) / 실패(시도 횟수 +1, 키워드 비움), 건마다 짧은 트랜잭션
 * API 호출 동안에는 트랜잭션을 잡지 않음
 */
@Component
@RequiredArgsConstructor
public class ArticleAiResultRecorder {

    private final ArticleRepository articleRepository;
    private final ArticleEmbeddingRepository articleEmbeddingRepository;

    /**
     * 번역은 검증 통과분만 (원문에 없는 숫자열 등은 버리고 주제만 반영), 키워드도 검증 통과분으로 교체
     */
    @Transactional
    public void recordSuccess(Long articleId, ArticleTitleAnalysis analysis) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        String acceptedTranslatedTitle = ArticleTitleAnalysisPolicy.toAcceptedTranslation(
                article.getTitle(), article.getRegion(), analysis.translatedTitle());
        String acceptedSubjectName = ArticleTitleAnalysisPolicy.toAcceptedSubjectName(
                article.getTitle(), analysis.subjectName());
        List<String> acceptedKeywords = ArticleTitleAnalysisPolicy.toAcceptedKeywords(
                article.getTitle(), analysis.keywords());
        article.applyAiAnalysis(analysis.topic(), acceptedTranslatedTitle, analysis.importance(), acceptedSubjectName);
        article.replaceKeywords(acceptedKeywords);
        // 임베딩 입력(번역 제목·키워드)이 바뀜: AI 대기 기한을 넘겨 원문 제목만으로 만든 임베딩이 있으면 지워 다시 만들게 함
        articleEmbeddingRepository.deleteByArticleId(articleId);
    }

    @Transactional
    public void recordFailure(Long articleId) {
        articleRepository.findById(articleId).orElseThrow().recordAiFailure();
    }
}
