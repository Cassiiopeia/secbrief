package kr.suhsaechan.secbrief.advisory.collector;

import java.util.List;

/**
 * 출처 1곳 수집 결과: 변경 없음 여부, 다음 수집에 쓸 검증값, 원본 항목 목록
 * 변경 없음이면 항목은 빈 목록 (호출부는 저장 없이 성공으로 기록)
 */
public record CollectOutcome(boolean isNotModified, FeedValidators feedValidators, List<CollectedArticle> articles) {

    /**
     * 조건부 요청을 안 쓰는 수집기용 (검증값 없음)
     */
    public static CollectOutcome ofArticles(List<CollectedArticle> articles) {
        return new CollectOutcome(false, FeedValidators.NONE, articles);
    }

    public static CollectOutcome ofArticles(List<CollectedArticle> articles, FeedValidators feedValidators) {
        return new CollectOutcome(false, feedValidators, articles);
    }

    public static CollectOutcome notModified(FeedValidators feedValidators) {
        return new CollectOutcome(true, feedValidators, List.of());
    }
}
