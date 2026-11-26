package kr.suhsaechan.secbrief.advisory.image;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import kr.suhsaechan.secbrief.advisory.collector.FeedHttpClient;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

/**
 * 기사 대표 이미지 내려받기: 주소 검사 > GET > 리다이렉트는 직접 따라가며 다시 검사 > 종류·크기 확인 > 원본 바이트
 * 피드가 준 주소라 SSRF 방어 필수: http(s) 만, 접속 주소는 공인 주소만 (검사한 그 주소로 접속), 프록시 미사용
 */
@Component
public class ArticleImageDownloader {

    // 내려받는 원본 최대 크기 (5MB, 압축 해제 후 기준): 넘으면 읽기를 멈추고 거부
    public static final int MAXIMUM_IMAGE_BYTES = 5 * 1024 * 1024;
    // 직접 따라가는 리다이렉트 최대 횟수
    static final int MAXIMUM_REDIRECT_COUNT = 3;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    // 요청 1회 전체 상한 (조금씩 흘려보내 붙잡는 응답 차단)
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);
    private static final String IMAGE_CONTENT_TYPE_PREFIX = "image/";
    // OkHttp 가 DNS 조회 없이 바로 접속하는 호스트 모양 (IP 를 직접 적은 주소): 이 경우 Dns 검사를 타지 않아 따로 검사
    private static final Pattern IP_LITERAL_HOST = Pattern.compile("([0-9a-fA-F]*:[0-9a-fA-F:.]*)|([\\d.]+)");
    // 숫자와 점만 있는 호스트는 a.b.c.d 꼴만 받음 (127.1 같은 줄임 표기·숫자 도메인 거부)
    private static final Pattern DOTTED_QUAD_HOST = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final String IPV6_SEPARATOR = ":";

    private final ImageHostAddressValidator imageHostAddressValidator;
    private final OkHttpClient httpClient;

    public ArticleImageDownloader(ImageHostAddressValidator imageHostAddressValidator) {
        this.imageHostAddressValidator = imageHostAddressValidator;
        this.httpClient = new OkHttpClient.Builder()
                // 조회한 주소를 검사해 그대로 접속에 씀 (검사 뒤 DNS 응답이 바뀌는 재바인딩 차단)
                .dns(this::lookupAllowedAddresses)
                // 프록시를 타면 프록시가 주소를 풀어 검사가 무력해짐
                .proxy(Proxy.NO_PROXY)
                // 리다이렉트는 직접 따라가며 매번 검사
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(CONNECT_TIMEOUT)
                .readTimeout(READ_TIMEOUT)
                .callTimeout(CALL_TIMEOUT)
                .build();
    }

    /**
     * 이미지 주소 > 원본 바이트 (리다이렉트 최대 3회, 매번 주소 검사)
     */
    public byte[] download(String imageUrl) {
        HttpUrl currentUrl = toCheckedUrl(imageUrl);
        for (int redirectCount = 0; ; redirectCount++) {
            try (Response response = httpClient.newCall(toRequest(currentUrl)).execute()) {
                if (!response.isRedirect()) {
                    return readImageBody(response);
                }
                if (redirectCount >= MAXIMUM_REDIRECT_COUNT) {
                    throw rejected("too many redirects");
                }
                currentUrl = toRedirectTargetUrl(currentUrl, response.header("Location"));
            } catch (IOException exception) {
                // 연결 실패·시간 초과: 원인 종류만 남김 (주소는 기록하지 않음)
                throw rejected("unreachable : " + exception.getClass().getSimpleName());
            }
        }
    }

    // http(s) + 호스트 있음 + 계정 정보 없음, IP 를 직접 적은 주소면 바로 검사
    private HttpUrl toCheckedUrl(String imageUrl) {
        // HttpUrl 은 http·https 만 해석 (그 밖의 스킴·형식 오류는 null)
        HttpUrl url = imageUrl == null ? null : HttpUrl.parse(imageUrl);
        if (url == null) {
            throw rejected("invalid url");
        }
        if (!url.username().isEmpty() || !url.password().isEmpty()) {
            throw rejected("url with credentials");
        }
        if (IP_LITERAL_HOST.matcher(url.host()).matches()) {
            requireAllowedAddress(toLiteralAddress(url.host()));
        }
        return url;
    }

    // 상대 주소는 현재 주소 기준으로 풀고 처음 주소와 같은 검사
    private HttpUrl toRedirectTargetUrl(HttpUrl currentUrl, String location) {
        HttpUrl targetUrl = location == null ? null : currentUrl.resolve(location);
        if (targetUrl == null) {
            throw rejected("invalid redirect");
        }
        return toCheckedUrl(targetUrl.toString());
    }

    // 2xx + Content-Type image/* + 크기 상한 이내
    private static byte[] readImageBody(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw rejected("status=" + response.code());
        }
        String contentType = response.header("Content-Type", "");
        if (!contentType.strip().toLowerCase(Locale.ROOT).startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
            throw rejected("not an image");
        }
        ResponseBody responseBody = response.body();
        if (responseBody == null || responseBody.contentLength() > MAXIMUM_IMAGE_BYTES) {
            throw rejected("too large or empty");
        }
        // Content-Length 가 없거나 거짓일 수 있어 상한 + 1 까지만 읽고 판정
        byte[] imageBytes = responseBody.byteStream().readNBytes(MAXIMUM_IMAGE_BYTES + 1);
        if (imageBytes.length == 0 || imageBytes.length > MAXIMUM_IMAGE_BYTES) {
            throw rejected("too large or empty");
        }
        return imageBytes;
    }

    // 호스트 이름 조회 > 전부 허용 주소일 때만 반환 (하나라도 내부 주소면 거부)
    private List<InetAddress> lookupAllowedAddresses(String hostname) throws UnknownHostException {
        List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
        addresses.forEach(this::requireAllowedAddress);
        return addresses;
    }

    private void requireAllowedAddress(InetAddress address) {
        if (!imageHostAddressValidator.isAllowed(address)) {
            throw rejected("blocked address");
        }
    }

    // IP 를 직접 적은 호스트 > 주소 (DNS 조회 없음, 해석 못 하면 거부)
    private static InetAddress toLiteralAddress(String ipLiteralHost) {
        if (!ipLiteralHost.contains(IPV6_SEPARATOR) && !DOTTED_QUAD_HOST.matcher(ipLiteralHost).matches()) {
            throw rejected("invalid url");
        }
        try {
            return InetAddress.getByName(ipLiteralHost);
        } catch (UnknownHostException exception) {
            throw rejected("invalid url");
        }
    }

    private static Request toRequest(HttpUrl url) {
        return new Request.Builder().url(url).header("User-Agent", FeedHttpClient.USER_AGENT).build();
    }

    // 거부·실패 공통: EXTERNAL_API_ERROR (detail 에 원인, 주소는 싣지 않음)
    private static CustomException rejected(String detail) {
        return new CustomException(ErrorCode.EXTERNAL_API_ERROR, detail);
    }
}
