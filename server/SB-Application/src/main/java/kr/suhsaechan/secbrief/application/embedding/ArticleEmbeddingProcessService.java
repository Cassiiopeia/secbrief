package kr.suhsaechan.secbrief.application.embedding;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.suhsaechan.secbrief.advisory.embedding.ArticleEmbeddingText;
import kr.suhsaechan.secbrief.advisory.embedding.TextEmbeddingProvider;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.repository.ArticleEmbeddingRepository;
import kr.suhsaechan.secbrief.advisory.search.ArticleEmbeddingIndex;
import kr.suhsaechan.secbrief.common.exception.ExceptionLogText;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 임베딩 묶음 처리: 제공자 확인 > 임베딩이 없는(또는 다른 모델로 만든) 최근 기사 batchSize 건 > requestBatchSize 건씩 묶어 임베딩 > 저장
 * 제공자 호출 실패는 기록 없이 틱을 멈춤 (다음 틱에 다시), 제목은 로그에 남기지 않음
 */
@Slf4j
@Service
public class ArticleEmbeddingProcessService {

    // AI 처리(번역 제목·키워드)를 기다리는 시간: 넘기면 원문 제목만으로 임베딩 (AI 미설정 환경에서도 검색되게)
    // 그 뒤 AI 결과가 기록되면 임베딩을 지워 다시 만듦 (ArticleAiResultRecorder)
    public static final Duration AI_WAIT_DURATION = Duration.ofMinutes(30);

    private final TextEmbeddingProvider textEmbeddingProvider;
    private final ArticleEmbeddingRepository articleEmbeddingRepository;
    private final ArticleEmbeddingResultRecorder articleEmbeddingResultRecorder;
    private final Clock clock;
    // 한 틱에 처리할 기사 수
    private final int batchSize;
    // 제공자 호출 1회에 넣을 기사 수
    private final int requestBatchSize;

    public ArticleEmbeddingProcessService(TextEmbeddingProvider textEmbeddingProvider,
                                          ArticleEmbeddingRepository articleEmbeddingRepository,
                                          ArticleEmbeddingResultRecorder articleEmbeddingResultRecorder, Clock clock,
                                          @Value("${secbrief.embedding.batch-size:100}") int batchSize,
                                          @Value("${secbrief.embedding.request-batch-size:25}") int requestBatchSize) {
        this.textEmbeddingProvider = textEmbeddingProvider;
        this.articleEmbeddingRepository = articleEmbeddingRepository;
        this.articleEmbeddingResultRecorder = articleEmbeddingResultRecorder;
        this.clock = clock;
        this.batchSize = batchSize;
        // 0 이하 설정은 1건씩 (나누기 반복이 멈추지 않는 것 방지)
        this.requestBatchSize = Math.max(1, requestBatchSize);
    }

    public EmbeddingProcessResult processMissingEmbeddings() {
        if (!textEmbeddingProvider.isAvailable()) {
            log.info("[article embedding] skipped : provider not configured");
            return new EmbeddingProcessResult(0, true);
        }

        Instant now = clock.instant();
        List<Article> targetArticles = articleEmbeddingRepository.findEmbeddingTargets(
                textEmbeddingProvider.getModelName(), now.minus(ArticleEmbeddingIndex.SEARCHABLE_PUBLISHED_WITHIN),
                now.minus(AI_WAIT_DURATION), Limit.of(batchSize));
        int embeddedCount = 0;
        for (int fromIndex = 0; fromIndex < targetArticles.size(); fromIndex += requestBatchSize) {
            int toIndex = Math.min(fromIndex + requestBatchSize, targetArticles.size());
            List<Article> batchArticles = targetArticles.subList(fromIndex, toIndex);
            if (!embedAndRecordBatch(batchArticles)) {
                // 제공자 장애·기록 실패: 남은 묶음도 같은 이유로 실패하므로 멈춤
                break;
            }
            embeddedCount += batchArticles.size();
        }
        if (!targetArticles.isEmpty()) {
            log.info("[article embedding] processed : embedded={}, pending={}",
                    embeddedCount, targetArticles.size() - embeddedCount);
        }
        return new EmbeddingProcessResult(embeddedCount, false);
    }

    /**
     * 묶음 1회: 입력 문장 만들기 > 임베딩 > 저장, 반환은 성공 여부 (실패면 아무것도 기록하지 않음)
     */
    private boolean embedAndRecordBatch(List<Article> articles) {
        List<float[]> embeddings;
        try {
            embeddings = textEmbeddingProvider.embed(articles.stream().map(ArticleEmbeddingText::from).toList());
        } catch (RuntimeException exception) {
            log.warn("[article embedding] failed 502 : provider call failed, retry next tick : size={}, cause={}",
                    articles.size(), ExceptionLogText.toLogCause(exception));
            return false;
        }
        // 규약 위반(벡터 수 불일치): 어느 기사의 벡터인지 알 수 없어 기록하지 않음
        if (embeddings.size() != articles.size()) {
            log.warn("[article embedding] failed : embedding count mismatch : size={}", articles.size());
            return false;
        }

        Map<Long, float[]> embeddingByArticleId = new LinkedHashMap<>();
        for (int articleIndex = 0; articleIndex < articles.size(); articleIndex++) {
            embeddingByArticleId.put(articles.get(articleIndex).getId(), embeddings.get(articleIndex));
        }
        try {
            articleEmbeddingResultRecorder.recordEmbeddings(textEmbeddingProvider.getModelName(), embeddingByArticleId);
            return true;
        } catch (RuntimeException exception) {
            log.error("[article embedding] record failed : size={}, cause={}",
                    articles.size(), exception.getClass().getSimpleName());
            return false;
        }
    }
}
