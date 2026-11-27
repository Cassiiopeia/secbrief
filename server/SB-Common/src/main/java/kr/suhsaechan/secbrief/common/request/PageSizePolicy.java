package kr.suhsaechan.secbrief.common.request;

/**
 * 목록 API 공통 쪽 크기 규칙: 거부 없이 조정 (null·1 미만 > 기본값, 최대 초과 > 최대값)
 * 적용 4곳: 기사 목록, 기사 검색, 인기 키워드, 알림함
 */
public final class PageSizePolicy {

    private PageSizePolicy() {
    }

    /**
     * 요청 크기 > 쓸 크기 (null·1 미만 > defaultSize, maximumSize 초과 > maximumSize)
     */
    public static int toClampedPageSize(Integer requestedSize, int defaultSize, int maximumSize) {
        if (requestedSize == null || requestedSize < 1) {
            return defaultSize;
        }
        return Math.min(requestedSize, maximumSize);
    }
}
