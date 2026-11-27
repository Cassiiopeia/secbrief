package kr.suhsaechan.secbrief.advisory.repository;

import java.time.Instant;
import java.util.List;
import kr.suhsaechan.secbrief.advisory.entity.Article;
import kr.suhsaechan.secbrief.advisory.entity.ArticleEmbedding;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleEmbeddingRepository extends JpaRepository<ArticleEmbedding, Long> {

    // 임베딩 대기열: 이 모델의 임베딩이 없는 최근 게시 기사, 최신 항목부터 limit 건
    // AI 처리가 끝났거나 AI 대기 기한(aiWaitDeadline 이전 수집)을 넘긴 것만 (번역 제목·키워드까지 넣으려고 기다림)
    @Query("select a from Article a "
            + "where a.publishedAt >= :publishedSince "
            + "and (a.aiAnalysisState.status <> kr.suhsaechan.secbrief.advisory.constant.AiStatus.PENDING or a.collectedAt <= :aiWaitDeadline) "
            + "and not exists (select 1 from ArticleEmbedding e where e.articleId = a.id and e.embeddingModel = :embeddingModel) "
            + "order by a.id desc")
    List<Article> findEmbeddingTargets(@Param("embeddingModel") String embeddingModel,
                                       @Param("publishedSince") Instant publishedSince,
                                       @Param("aiWaitDeadline") Instant aiWaitDeadline, Limit limit);

    // 메모리 캐시 적재: 이 모델로 만든 최근 게시 기사의 임베딩, 게시일 최신순 limit 건
    @Query("select new kr.suhsaechan.secbrief.advisory.repository.StoredEmbedding(e.articleId, e.embedding, e.createdAt) "
            + "from ArticleEmbedding e join Article a on a.id = e.articleId "
            + "where e.embeddingModel = :embeddingModel and a.publishedAt >= :publishedSince "
            + "order by a.publishedAt desc, a.id desc")
    List<StoredEmbedding> findRecentEmbeddings(@Param("embeddingModel") String embeddingModel,
                                               @Param("publishedSince") Instant publishedSince, Limit limit);

    // 메모리 캐시 추가분: createdAfter 뒤에 만든 것만
    @Query("select new kr.suhsaechan.secbrief.advisory.repository.StoredEmbedding(e.articleId, e.embedding, e.createdAt) "
            + "from ArticleEmbedding e join Article a on a.id = e.articleId "
            + "where e.embeddingModel = :embeddingModel and a.publishedAt >= :publishedSince and e.createdAt > :createdAfter "
            + "order by e.createdAt asc")
    List<StoredEmbedding> findEmbeddingsCreatedAfter(@Param("embeddingModel") String embeddingModel,
                                                     @Param("publishedSince") Instant publishedSince,
                                                     @Param("createdAfter") Instant createdAfter, Limit limit);

    // 기사의 임베딩 삭제 (없으면 0): 입력 문장이 바뀐 기사를 대기열로 되돌림
    @Modifying
    @Query("delete from ArticleEmbedding e where e.articleId = :articleId")
    int deleteByArticleId(@Param("articleId") Long articleId);
}
