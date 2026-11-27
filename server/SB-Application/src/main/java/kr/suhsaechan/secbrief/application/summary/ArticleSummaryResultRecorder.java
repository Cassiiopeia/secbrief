package kr.suhsaechan.secbrief.application.summary;

import java.util.Objects;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.ArticleSummaryState;
import kr.suhsaechan.secbrief.advisory.repository.ArticleRepository;
import kr.suhsaechan.secbrief.advisory.summary.ArticleBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요약 결과 기록: 성공(브리핑 반영 > DONE) / 실패(시도 횟수 +1, 3회면 FAILED) / 다시 받은 입력의 브리핑, 끝나면 요약 입력을 비움
 * 건마다 짧은 트랜잭션 + 행 잠금 (수집 병합과 같은 행을 동시에 고치지 않게), API 호출 동안에는 트랜잭션을 잡지 않음
 */
@Component
@RequiredArgsConstructor
public class ArticleSummaryResultRecorder {

    private final ArticleRepository articleRepository;

    /**
     * 검증 통과한 브리핑 반영. 요약하는 사이 입력이 바뀌었으면(NVD 링크 교체) 기록하지 않고 false: 새 입력으로 다음 틱에 다시
     */
    @Transactional
    public boolean recordSuccess(Long articleId, String summarizedSourceText, ArticleBrief acceptedBrief) {
        Article article = articleRepository.findByIdForUpdate(articleId).orElseThrow();
        if (!Objects.equals(article.getSummaryState().getSourceText(), summarizedSourceText)) {
            return false;
        }
        article.getSummaryState().applyBrief(acceptedBrief);
        return true;
    }

    /**
     * 다시 받은 입력으로 만든 브리핑 반영 (서버 다시 요약): 그 사이 브리핑이 생겼거나 새 입력으로 대기 중이면 기록하지 않고 false
     */
    @Transactional
    public boolean recordRefetchedBrief(Long articleId, ArticleBrief acceptedBrief) {
        ArticleSummaryState summaryState = articleRepository.findByIdForUpdate(articleId).orElseThrow().getSummaryState();
        if (summaryState.getOneLine() != null || summaryState.getSourceText() != null) {
            return false;
        }
        summaryState.applyBrief(acceptedBrief);
        return true;
    }

    /**
     * 실패 기록. 요약하는 사이 입력이 바뀌었으면 새 입력의 시도 횟수를 쓰지 않음
     */
    @Transactional
    public void recordFailure(Long articleId, String summarizedSourceText) {
        Article article = articleRepository.findByIdForUpdate(articleId).orElseThrow();
        if (Objects.equals(article.getSummaryState().getSourceText(), summarizedSourceText)) {
            article.getSummaryState().recordFailure();
        }
    }
}
