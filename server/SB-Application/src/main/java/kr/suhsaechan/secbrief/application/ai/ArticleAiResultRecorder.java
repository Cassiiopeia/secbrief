package kr.suhsaechan.secbrief.application.ai;

import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysis;
import kr.suhsaechan.secbrief.advisory.ai.ArticleTitleAnalysisPolicy;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 결과 기록: 성공(번역 검증 > 반영) / 실패(시도 횟수 +1), 건마다 짧은 트랜잭션
 * API 호출 동안에는 트랜잭션을 잡지 않음
 */
@Component
@RequiredArgsConstructor
public class ArticleAiResultRecorder {

    private final ArticleRepository articleRepository;

    /**
     * 번역은 검증 통과분만 (원문에 없는 숫자열 등은 버리고 주제만 반영)
     */
    @Transactional
    public void recordSuccess(Long articleId, ArticleTitleAnalysis analysis) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        String acceptedTranslatedTitle = ArticleTitleAnalysisPolicy.toAcceptedTranslation(
                article.getTitle(), article.getRegion(), analysis.translatedTitle());
        article.applyAiAnalysis(analysis.topic(), acceptedTranslatedTitle);
    }

    @Transactional
    public void recordFailure(Long articleId) {
        articleRepository.findById(articleId).orElseThrow().recordAiFailure();
    }
}
