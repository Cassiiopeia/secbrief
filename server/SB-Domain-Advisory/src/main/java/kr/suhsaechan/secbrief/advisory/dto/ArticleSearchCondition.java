package kr.suhsaechan.secbrief.advisory.dto;

import kr.suhsaechan.secbrief.advisory.constant.ArticleImportance;
import kr.suhsaechan.secbrief.advisory.constant.ArticleKind;
import kr.suhsaechan.secbrief.advisory.constant.ArticleRegion;
import kr.suhsaechan.secbrief.advisory.constant.ArticleTopic;
import kr.suhsaechan.secbrief.common.request.PageSizePolicy;

/**
 * 기사 목록 조건: 필터 7개(종류, 지역, 주제, 최소 중요도, 출처 코드, 키워드, 긴급만 — null 이면 전체) + 분류 조건 + 커서 + 크기
 */
public record ArticleSearchCondition(ArticleKind kind, ArticleRegion region, ArticleTopic topic,
                                     ArticleImportance minimumImportance, String sourceCode,
                                     // 키워드: 정확히 일치 (대소문자 무시)
                                     String keyword,
                                     // true 면 긴급 소식만 (경보단계 화면의 이번 주 긴급 건수에서 들어옴, #96)
                                     Boolean isUrgent,
                                     String cursor, Integer size,
                                     ArticleTagFilter tagFilter) {

    public ArticleSearchCondition {
        tagFilter = tagFilter == null ? ArticleTagFilter.none() : tagFilter;
    }

    /** 분류 조건 없이 (#93 이전 조건) */
    public ArticleSearchCondition(ArticleKind kind, ArticleRegion region, ArticleTopic topic,
                                  ArticleImportance minimumImportance, String sourceCode, String keyword,
                                  Boolean isUrgent, String cursor, Integer size) {
        this(kind, region, topic, minimumImportance, sourceCode, keyword, isUrgent, cursor, size, ArticleTagFilter.none());
    }

    /** 긴급 필터 없는 조건 (긴급 필터 이전 호출부) */
    public ArticleSearchCondition(ArticleKind kind, ArticleRegion region, ArticleTopic topic,
                                  ArticleImportance minimumImportance, String sourceCode, String keyword,
                                  String cursor, Integer size) {
        this(kind, region, topic, minimumImportance, sourceCode, keyword, null, cursor, size, ArticleTagFilter.none());
    }

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAXIMUM_PAGE_SIZE = 50;

    /**
     * 페이지 크기: null·1 미만이면 20, 50 넘으면 50 (PageSizePolicy)
     */
    public int toPageSize() {
        return PageSizePolicy.toClampedPageSize(size, DEFAULT_PAGE_SIZE, MAXIMUM_PAGE_SIZE);
    }
}
