package kr.suhsaechan.secbrief.advisory.collector;

/**
 * 조건부 GET 결과: 변경 없음(304, 본문 null) 또는 본문 + 응답의 새 검증값
 * 변경 없음이면 validators 는 이전 값 유지 (304 응답이 새 값을 주면 그 값)
 */
public record FeedFetchResult(boolean isNotModified, byte[] body, FeedValidators validators) {

    public static FeedFetchResult notModified(FeedValidators validators) {
        return new FeedFetchResult(true, null, validators);
    }

    public static FeedFetchResult modified(byte[] body, FeedValidators validators) {
        return new FeedFetchResult(false, body, validators);
    }
}
