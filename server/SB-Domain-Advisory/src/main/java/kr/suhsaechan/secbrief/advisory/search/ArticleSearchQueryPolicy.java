package kr.suhsaechan.secbrief.advisory.search;

import java.util.Locale;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import kr.suhsaechan.secbrief.common.request.PageSizePolicy;

/**
 * 검색 요청 규칙: 질의 정리·길이 확인(2~100자), 결과 크기(기본 30, 최대 50으로 조정), CVE 번호 질의 판정
 * 질의 문자열은 예외 메시지·로그에 넣지 않음 (길이만)
 */
public final class ArticleSearchQueryPolicy {

    public static final int MINIMUM_QUERY_LENGTH = 2;
    public static final int MAXIMUM_QUERY_LENGTH = 100;
    public static final int DEFAULT_SEARCH_SIZE = 30;
    public static final int MAXIMUM_SEARCH_SIZE = 50;
    // CVE 번호 질의 접두사 (소문자 비교): 번호는 뜻이 아니라 글자로 찾아야 맞음
    private static final String CVE_ID_QUERY_PREFIX = "cve-";

    private ArticleSearchQueryPolicy() {
    }

    /**
     * 검색어: trim 후 2~100자, 벗어나면 INVALID_REQUEST
     */
    public static String toSearchText(String query) {
        String searchText = query == null ? "" : query.strip();
        if (searchText.length() < MINIMUM_QUERY_LENGTH || searchText.length() > MAXIMUM_QUERY_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_REQUEST, "query length out of range");
        }
        return searchText;
    }

    /**
     * 결과 크기: null·1 미만이면 30, 50 넘으면 50 (PageSizePolicy)
     */
    public static int toSearchSize(Integer size) {
        return PageSizePolicy.toClampedPageSize(size, DEFAULT_SEARCH_SIZE, MAXIMUM_SEARCH_SIZE);
    }

    /** CVE 번호로 찾는 질의인지 (의미 검색을 거치지 않고 글자 일치로 찾음) */
    public static boolean isCveIdQuery(String searchText) {
        return searchText.toLowerCase(Locale.ROOT).startsWith(CVE_ID_QUERY_PREFIX);
    }
}
