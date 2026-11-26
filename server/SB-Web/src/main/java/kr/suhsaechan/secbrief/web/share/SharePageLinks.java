package kr.suhsaechan.secbrief.web.share;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * 공유 페이지 이동 주소 모음: 앱 열기(스킴·Android intent), 스토어 2곳, 앱 아이콘
 * 값 원본은 ShareLinkProperties (application.yml secbrief.share-link)
 */
@Component
public class SharePageLinks {

    // 앱 기사 상세 경로 (앱 app/articles/[articleId] 와 같아야 함): secbrief://articles/{id}
    private static final String APP_ARTICLE_PATH_FORMAT = "articles/%d";
    // 앱 첫 화면 (기사를 못 찾았을 때)
    private static final String APP_HOME_PATH = "";
    private static final String APP_STORE_URL_FORMAT = "https://apps.apple.com/kr/app/id%s";
    private static final String PLAY_STORE_URL_FORMAT = "https://play.google.com/store/apps/details?id=%s";
    // Android 크롬: 앱이 있으면 열고, 없으면 browser_fallback_url(Play 스토어)로 이동
    private static final String ANDROID_INTENT_URL_FORMAT = "intent://%s#Intent;scheme=%s;package=%s;S.browser_fallback_url=%s;end";

    private final ShareLinkProperties shareLinkProperties;

    public SharePageLinks(ShareLinkProperties shareLinkProperties) {
        this.shareLinkProperties = shareLinkProperties;
    }

    /** 기사 미리보기 공개 주소 (공유 주소, canonical) */
    public String toArticlePageUrl(Long articleId) {
        return shareLinkProperties.toPublicUrl(SharePaths.ARTICLE_PREVIEW_PREFIX + articleId);
    }

    /** 기사 > 앱 스킴 주소 (secbrief://articles/{id}) */
    public String toAppArticleUrl(Long articleId) {
        return toAppUrl(APP_ARTICLE_PATH_FORMAT.formatted(articleId));
    }

    /** 기사 > Android intent 주소 (앱 없으면 Play 스토어) */
    public String toAndroidArticleIntentUrl(Long articleId) {
        return toAndroidIntentUrl(APP_ARTICLE_PATH_FORMAT.formatted(articleId));
    }

    public String getAppHomeUrl() {
        return toAppUrl(APP_HOME_PATH);
    }

    public String getAndroidHomeIntentUrl() {
        return toAndroidIntentUrl(APP_HOME_PATH);
    }

    public String getAppStoreUrl() {
        return APP_STORE_URL_FORMAT.formatted(shareLinkProperties.ios().appStoreId());
    }

    public String getPlayStoreUrl() {
        return PLAY_STORE_URL_FORMAT.formatted(shareLinkProperties.android().packageName());
    }

    public String getAppStoreId() {
        return shareLinkProperties.ios().appStoreId();
    }

    /** 앱 아이콘 공개 절대 주소 (og:image 기본값) */
    public String getAppIconUrl() {
        return shareLinkProperties.toPublicUrl(SharePaths.APP_ICON);
    }

    private String toAppUrl(String appPath) {
        return shareLinkProperties.appScheme() + "://" + appPath;
    }

    private String toAndroidIntentUrl(String appPath) {
        return ANDROID_INTENT_URL_FORMAT.formatted(appPath, shareLinkProperties.appScheme(),
                shareLinkProperties.android().packageName(),
                URLEncoder.encode(getPlayStoreUrl(), StandardCharsets.UTF_8));
    }
}
