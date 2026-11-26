package kr.suhsaechan.secbrief.web.share;

/**
 * 공유 링크 경로 단일 지점: 기사 미리보기, 앱 링크 연결 파일 2개, 미리보기 정적 파일
 * 앱 share-link-config.ts(공유 주소)·+native-intent(앱 경로 변환)와 함께 바꿈
 */
public final class SharePaths {

    // 기사 미리보기: /a/{articleId} (예: /a/123)
    public static final String ARTICLE_PREVIEW_PREFIX = "/a/";
    public static final String ARTICLE_PREVIEW = ARTICLE_PREVIEW_PREFIX + "{articleId}";
    // iOS 유니버설 링크 연결 파일 (확장자 없음, JSON)
    public static final String APPLE_APP_SITE_ASSOCIATION = "/.well-known/apple-app-site-association";
    // Android 앱 링크 연결 파일
    public static final String ANDROID_ASSET_LINKS = "/.well-known/assetlinks.json";
    // 미리보기 정적 파일 (앱 아이콘, 빌드 시 mobile/assets/images/icon.png 에서 복사)
    public static final String STATIC_PATTERN = "/share/**";
    public static final String APP_ICON = "/share/app-icon.png";
    // 로그인 없이 여는 공유 경로 (보안 공개 체인)
    public static final String[] PUBLIC_MATCHERS = {
            ARTICLE_PREVIEW_PREFIX + "**", APPLE_APP_SITE_ASSOCIATION, ANDROID_ASSET_LINKS, STATIC_PATTERN};

    private SharePaths() {
    }
}
