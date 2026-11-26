package kr.suhsaechan.secbrief.advisory.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * 벡터 계산: 정규화(길이 1), 영벡터·빈 벡터·숫자 아님은 빈 값, 정규화 벡터의 내적 = 코사인 유사도
 */
class EmbeddingVectorsTest {

    @Test
    void normalizesToUnitLength() {
        float[] normalizedVector = EmbeddingVectors.toNormalized(new float[] {3f, 4f}).orElseThrow();

        assertThat(normalizedVector[0]).isCloseTo(0.6f, within(1e-6f));
        assertThat(normalizedVector[1]).isCloseTo(0.8f, within(1e-6f));
    }

    @Test
    void returnsEmptyForVectorsWithoutDirection() {
        assertThat(EmbeddingVectors.toNormalized(new float[] {0f, 0f})).isEmpty();
        assertThat(EmbeddingVectors.toNormalized(new float[0])).isEmpty();
        assertThat(EmbeddingVectors.toNormalized(null)).isEmpty();
        assertThat(EmbeddingVectors.toNormalized(new float[] {Float.NaN, 1f})).isEmpty();
    }

    @Test
    void dotProductOfNormalizedVectorsIsCosineSimilarity() {
        float[] first = EmbeddingVectors.toNormalized(new float[] {1f, 0f}).orElseThrow();
        float[] sameDirection = EmbeddingVectors.toNormalized(new float[] {5f, 0f}).orElseThrow();
        float[] orthogonal = EmbeddingVectors.toNormalized(new float[] {0f, 2f}).orElseThrow();
        float[] opposite = EmbeddingVectors.toNormalized(new float[] {-3f, 0f}).orElseThrow();

        assertThat(EmbeddingVectors.toDotProduct(first, sameDirection)).isCloseTo(1.0, within(1e-6));
        assertThat(EmbeddingVectors.toDotProduct(first, orthogonal)).isCloseTo(0.0, within(1e-6));
        assertThat(EmbeddingVectors.toDotProduct(first, opposite)).isCloseTo(-1.0, within(1e-6));
    }
}
