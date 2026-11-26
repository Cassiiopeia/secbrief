package kr.suhsaechan.secbrief.web.share;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 링크 연결 파일 2개 (인증 없음, JSON, 이동 없음): iOS apple-app-site-association, Android assetlinks.json
 * 대상 경로는 기사 미리보기(/a/*)만. 값 원본은 ShareLinkProperties
 */
@RestController
@RequiredArgsConstructor
public class AppLinkAssociationController {

    // 유니버설 링크·앱 링크로 앱이 여는 경로 (SharePaths.ARTICLE_PREVIEW 와 같은 범위)
    static final String APP_LINK_PATH_PATTERN = SharePaths.ARTICLE_PREVIEW_PREFIX + "*";
    private static final String ANDROID_HANDLE_ALL_URLS_RELATION = "delegate_permission/common.handle_all_urls";
    private static final String ANDROID_APP_NAMESPACE = "android_app";
    // Apple CDN·Google 이 다시 읽는 주기와 맞춰 1시간
    private static final CacheControl ASSOCIATION_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final ShareLinkProperties shareLinkProperties;

    /** iOS: appIDs+components(iOS 13 이상) 와 appID+paths(이전 형식) 함께 */
    @GetMapping(SharePaths.APPLE_APP_SITE_ASSOCIATION)
    public ResponseEntity<Map<String, Object>> getAppleAppSiteAssociation() {
        String appId = shareLinkProperties.ios().toAppId();
        Map<String, Object> appLinkDetail = Map.of(
                "appIDs", List.of(appId),
                "components", List.of(Map.of("/", APP_LINK_PATH_PATTERN)),
                "appID", appId,
                "paths", List.of(APP_LINK_PATH_PATTERN));
        return toJsonResponse(Map.of("applinks", Map.of("apps", List.of(), "details", List.of(appLinkDetail))));
    }

    /** Android: 패키지 + 서명 지문 목록 (Play 앱 서명 키 + 업로드 키) */
    @GetMapping(SharePaths.ANDROID_ASSET_LINKS)
    public ResponseEntity<List<Map<String, Object>>> getAndroidAssetLinks() {
        ShareLinkProperties.Android android = shareLinkProperties.android();
        Map<String, Object> target = Map.of(
                "namespace", ANDROID_APP_NAMESPACE,
                "package_name", android.packageName(),
                "sha256_cert_fingerprints", android.sha256CertFingerprints());
        return toJsonResponse(List.of(Map.of("relation", List.of(ANDROID_HANDLE_ALL_URLS_RELATION), "target", target)));
    }

    private static <T> ResponseEntity<T> toJsonResponse(T body) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(ASSOCIATION_CACHE)
                .body(body);
    }
}
