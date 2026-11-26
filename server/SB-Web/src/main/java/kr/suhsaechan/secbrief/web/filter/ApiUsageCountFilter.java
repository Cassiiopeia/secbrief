package kr.suhsaechan.secbrief.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageCounter;
import kr.suhsaechan.secbrief.application.admin.usage.ApiUsageGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 공개 API 이용 집계: 처리된 요청(4xx·5xx 제외)을 경로 묶음별로 메모리 카운터에 더함
 * 요청 경로만 봄 (IP, User-Agent, 기기 식별값을 읽지 않음). 빈으로 두지 않고 공개 API 체인에만 걺
 */
@RequiredArgsConstructor
public class ApiUsageCountFilter extends OncePerRequestFilter {

    private final ApiUsageCounter apiUsageCounter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if (response.getStatus() < HttpStatus.BAD_REQUEST.value()) {
            ApiUsageGroup.fromRequestPath(RequestPaths.withinApplication(request)).ifPresent(apiUsageCounter::increment);
        }
    }
}
