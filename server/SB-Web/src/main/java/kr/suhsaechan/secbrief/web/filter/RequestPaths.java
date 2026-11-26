package kr.suhsaechan.secbrief.web.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 필터용 요청 경로 읽기: 컨텍스트 경로를 뺀 경로 (예: /api/v1/sources, 쿼리 문자열 없음)
 */
public final class RequestPaths {

    private RequestPaths() {
    }

    // getServletPath 는 서블릿 매핑·테스트 환경에 따라 빈 값이라 요청 주소에서 직접 계산
    public static String withinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }
}
