package kr.suhsaechan.secbrief.advisory.collector;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 출처 호출 공통: GET > 응답 본문 바이트 (인코딩 해석은 파서가 함), 조건부 GET > 변경 없음 또는 본문 + 새 검증값
 * 2xx 가 아니거나 연결 실패면 EXTERNAL_API_ERROR, 주소가 여러 개면 연결 실패 시 다음 주소로 (IPv4 먼저: secbrief.feed-http.prefer-ipv4)
 */
@Component
public class FeedHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
    // 출처 운영자가 누구인지 알 수 있게 지원 페이지 주소를 함께 보냄
    public static final String USER_AGENT = "secbrief-collector/1.0 (+https://cassiiopeia.github.io/secbrief/support.html)";

    // 출처 주소의 IPv4 를 먼저 시도 (운영 설정 secbrief.feed-http.prefer-ipv4, 로컬 도구 --prefer-ipv4 의 기본값)
    public static final boolean SHOULD_PREFER_IPV4_BY_DEFAULT = true;

    private final RestClient restClient;

    @Autowired
    public FeedHttpClient(@Value("${secbrief.feed-http.prefer-ipv4}") boolean shouldPreferIpv4) {
        this(new FeedHostAddressResolver(shouldPreferIpv4));
    }

    // 조회 결과를 정해 두는 테스트용
    FeedHttpClient(FeedHostAddressResolver hostAddressResolver) {
        this.restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(toHttpClient(hostAddressResolver)))
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    // 연결 제한은 주소마다, 읽기 제한은 본문을 받는 중에도 적용 (멈춘 응답이 스케줄러 스레드를 붙잡지 않게)
    // 자동 재요청 끔 (503·429 를 그 자리에서 다시 부르지 않음: 다음 수집 주기에 다시), 쿠키 저장 안 함
    private static CloseableHttpClient toHttpClient(DnsResolver hostAddressResolver) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
                .setSocketTimeout(Timeout.of(READ_TIMEOUT))
                .build();
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDnsResolver(hostAddressResolver)
                        .setDefaultConnectionConfig(connectionConfig)
                        .build())
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.of(READ_TIMEOUT)).build())
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
    }

    public byte[] fetch(String url) {
        return fetch(url, Map.of());
    }

    /**
     * extraHeaders: 출처별 추가 헤더 (예: Accept)
     * url 은 호출부가 인코딩을 마친 값 (URI 로 넘겨 RestClient 가 다시 인코딩하지 않게 함)
     */
    public byte[] fetch(String url, Map<String, String> extraHeaders) {
        return exchange(url, extraHeaders, FeedValidators.NONE).body();
    }

    /**
     * 조건부 GET: 이전 검증값을 If-None-Match / If-Modified-Since 로 보냄 > 304 면 변경 없음, 200 이면 본문 + 새 검증값
     * 한 주소를 통째로 받는 출처용 (RSS, KEV), 이전 검증값이 없으면 일반 GET 과 같음
     */
    public FeedFetchResult fetchIfChanged(String url, FeedValidators previousValidators) {
        return exchange(url, Map.of(), previousValidators);
    }

    private FeedFetchResult exchange(String url, Map<String, String> extraHeaders, FeedValidators previousValidators) {
        try {
            return restClient.get()
                    .uri(URI.create(url))
                    .headers(headers -> {
                        extraHeaders.forEach(headers::set);
                        setConditionalHeaders(headers, previousValidators);
                    })
                    .exchange((request, response) -> {
                        FeedValidators responseValidators = toValidators(response.getHeaders());
                        // 304 는 검증값을 보낸 요청에서만 정상 응답 (본문 없음)
                        if (response.getStatusCode().value() == HttpStatus.NOT_MODIFIED.value()
                                && previousValidators.hasAnyValidator()) {
                            return FeedFetchResult.notModified(
                                    responseValidators.hasAnyValidator() ? responseValidators : previousValidators);
                        }
                        // 2xx 가 아니면 본문을 읽지 않고 상태 코드만 남김
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR,
                                    "status=" + response.getStatusCode().value());
                        }
                        return FeedFetchResult.modified(response.getBody().readAllBytes(), responseValidators);
                    });
        } catch (IllegalArgumentException exception) {
            // 주소 형식 오류 (출처 설정 문제): 연결 실패와 같은 코드로 기록
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR, "invalid url");
        } catch (RestClientException exception) {
            // 연결 실패·시간 초과: 원인 종류만 남김 (URL 은 기록하지 않음)
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR,
                    "unreachable : " + exception.getMostSpecificCause().getClass().getSimpleName());
        }
    }

    private static void setConditionalHeaders(HttpHeaders headers, FeedValidators previousValidators) {
        if (previousValidators.etag() != null) {
            headers.set(HttpHeaders.IF_NONE_MATCH, previousValidators.etag());
        }
        if (previousValidators.lastModified() != null) {
            headers.set(HttpHeaders.IF_MODIFIED_SINCE, previousValidators.lastModified());
        }
    }

    // 응답 헤더 값 그대로 (날짜로 해석하지 않음: 출처가 준 문자열을 그대로 돌려보내야 일치)
    private static FeedValidators toValidators(HttpHeaders responseHeaders) {
        return new FeedValidators(responseHeaders.getFirst(HttpHeaders.ETAG),
                responseHeaders.getFirst(HttpHeaders.LAST_MODIFIED));
    }
}
