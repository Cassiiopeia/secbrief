package kr.suhsaechan.secbrief.advisory.repository;

import java.time.Instant;

/**
 * 저장된 임베딩 1건 (메모리 캐시 적재용): 기사 ID, 벡터, 만든 시각
 */
public record StoredEmbedding(Long articleId, float[] embedding, Instant createdAt) {
}
