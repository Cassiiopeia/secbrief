package kr.suhsaechan.secbrief.advisory.classification;

import java.util.Map;
import kr.suhsaechan.secbrief.advisory.constant.ClassificationSource;

/**
 * 분류 묶음 1회 결과: 대상 수, 분야 근거별 건수, AI 분야 고르기로 돌린 수
 */
public record ClassificationBatchResult(int targetCount, Map<ClassificationSource, Integer> countBySource,
                                        int aiRequestedCount) {

    public int countOf(ClassificationSource classificationSource) {
        return countBySource.getOrDefault(classificationSource, 0);
    }
}
