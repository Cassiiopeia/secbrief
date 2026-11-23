package kr.suhsaechan.secbrief.advisory.dto;

import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;

/**
 * 기사 목록 조건: 필터 3개(종류, 지역, 주제, null 이면 전체) + 커서 + 크기
 */
public record ArticleSearchCondition(ArticleKind kind, ArticleRegion region, ArticleTopic topic, String cursor, Integer size) {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAXIMUM_PAGE_SIZE = 50;

    /**
     * 페이지 크기: null 이면 20, 1~50 밖이면 INVALID_REQUEST
     */
    public int toPageSize() {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAXIMUM_PAGE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "size out of range");
        }
        return size;
    }
}
