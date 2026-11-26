package kr.suhsaechan.secbrief.advisory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 기사 1건의 제목 임베딩 (의미 검색용): 기사당 1행, 어느 모델로 만들었는지 함께 보관
 * 모델을 바꾸거나 AI 결과(번역·키워드)가 바뀌면 다시 만듦
 */
@Entity
@Table(name = "article_embedding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArticleEmbedding {

    // article_embedding.embedding_model 열 길이
    public static final int MAXIMUM_EMBEDDING_MODEL_LENGTH = 100;

    // article.id 와 같은 값 (기사 삭제 시 함께 삭제)
    @Id
    private Long articleId;

    @Column(nullable = false, length = MAXIMUM_EMBEDDING_MODEL_LENGTH)
    private String embeddingModel;

    // 확장(pgvector) 없이 쓰려고 real 배열로 보관, 유사도는 서버 메모리에서 계산
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "real[]")
    private float[] embedding;

    // 만든 시각: 메모리 캐시가 이 시각 이후 것만 추가로 읽음
    @Column(nullable = false)
    private Instant createdAt;

    public static ArticleEmbedding create(Long articleId, String embeddingModel, float[] embedding, Instant createdAt) {
        ArticleEmbedding articleEmbedding = new ArticleEmbedding();
        articleEmbedding.articleId = articleId;
        articleEmbedding.replace(embeddingModel, embedding, createdAt);
        return articleEmbedding;
    }

    /**
     * 다시 만든 임베딩으로 교체 (모델 변경)
     */
    public void replace(String embeddingModel, float[] embedding, Instant createdAt) {
        this.embeddingModel = embeddingModel;
        this.embedding = embedding;
        this.createdAt = createdAt;
    }
}
