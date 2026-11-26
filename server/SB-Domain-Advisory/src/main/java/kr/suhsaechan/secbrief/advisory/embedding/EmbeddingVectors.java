package kr.suhsaechan.secbrief.advisory.embedding;

import java.util.Optional;

/**
 * 임베딩 벡터 계산: 길이 1로 정규화, 내적 (정규화한 벡터끼리의 내적 = 코사인 유사도)
 */
public final class EmbeddingVectors {

    private EmbeddingVectors() {
    }

    /**
     * 길이 1로 정규화한 새 벡터 (빈 벡터·영벡터·숫자 아님이 섞이면 빈 값: 유사도를 계산할 수 없음)
     */
    public static Optional<float[]> toNormalized(float[] vector) {
        if (vector == null || vector.length == 0) {
            return Optional.empty();
        }
        double squaredSum = 0;
        for (float value : vector) {
            squaredSum += (double) value * value;
        }
        double length = Math.sqrt(squaredSum);
        if (length == 0 || !Double.isFinite(length)) {
            return Optional.empty();
        }
        float[] normalizedVector = new float[vector.length];
        for (int dimensionIndex = 0; dimensionIndex < vector.length; dimensionIndex++) {
            normalizedVector[dimensionIndex] = (float) (vector[dimensionIndex] / length);
        }
        return Optional.of(normalizedVector);
    }

    /**
     * 내적 (차원이 같은 벡터끼리만 호출)
     */
    public static double toDotProduct(float[] first, float[] second) {
        double dotProduct = 0;
        for (int dimensionIndex = 0; dimensionIndex < first.length; dimensionIndex++) {
            dotProduct += (double) first[dimensionIndex] * second[dimensionIndex];
        }
        return dotProduct;
    }
}
