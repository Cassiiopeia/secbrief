package kr.suhsaechan.secbrief.advisory.collector;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 출처 호출 공통: GET > 응답 본문 바이트 (인코딩 해석은 파서가 함)
 * 2xx 가 아니거나 연결 실패면 EXTERNAL_API_ERROR
 */
@Component
public class FeedHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    // 출처 운영자가 누구인지 알 수 있게 지원 페이지 주소를 함께 보냄
    private static final String USER_AGENT = "secbrief-collector/1.0 (+https://cassiiopeia.github.io/secbrief/support.html)";

    private final RestClient restClient;

    public FeedHttpClient() {
        // http > https 같은 리다이렉트도 따라감
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    public byte[] fetch(String url) {
        return fetch(url, Map.of());
    }

    /**
     * extraHeaders: 출처별 추가 헤더 (예: Accept)
     */
    public byte[] fetch(String url, Map<String, String> extraHeaders) {
        try {
            return restClient.get()
                    .uri(url)
                    .headers(headers -> extraHeaders.forEach(headers::set))
                    .exchange((request, response) -> {
                        // 2xx 가 아니면 본문을 읽지 않고 상태 코드만 남김
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR,
                                    "status=" + response.getStatusCode().value());
                        }
                        return response.getBody().readAllBytes();
                    });
        } catch (RestClientException exception) {
            // 연결 실패·시간 초과: 원인 종류만 남김 (URL 은 기록하지 않음)
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR,
                    "unreachable : " + exception.getMostSpecificCause().getClass().getSimpleName());
        }
    }
}
