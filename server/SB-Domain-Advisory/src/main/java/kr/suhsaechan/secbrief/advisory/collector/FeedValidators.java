package kr.suhsaechan.secbrief.advisory.collector;

import kr.suhsaechan.secbrief.advisory.entity.Source;

/**
 * 피드 캐시 검증값: 출처가 응답에 준 ETag, Last-Modified (안 주면 null)
 * 다음 수집에 If-None-Match / If-Modified-Since 로 그대로 돌려보냄 (값을 해석하지 않음)
 */
public record FeedValidators(String etag, String lastModified) {

    // 검증값 없음: 조건부 요청을 안 쓰는 출처(NVD, GHSA), 첫 수집
    public static final FeedValidators NONE = new FeedValidators(null, null);

    /**
     * 출처에 저장된 이전 검증값
     */
    public static FeedValidators of(Source source) {
        return new FeedValidators(source.getFeedEtag(), source.getFeedLastModified());
    }

    public boolean hasAnyValidator() {
        return etag != null || lastModified != null;
    }
}
