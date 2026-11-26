package kr.suhsaechan.secbrief.web.share;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공유 링크·앱 링크 설정 (secbrief.share-link): 공개 주소, 앱 스킴, iOS 팀·번들·스토어 ID, Android 패키지·서명 지문
 * 기본값 원본은 application.yml (공개 값만, 비밀 아님)
 */
@ConfigurationProperties(prefix = "secbrief.share-link")
public record ShareLinkProperties(String publicBaseUrl, String appScheme, Ios ios, Android android) {

    /** iOS: 팀 ID(App ID 접두사), 번들 ID, App Store 앱 ID */
    public record Ios(String teamId, String bundleId, String appStoreId) {

        /** 유니버설 링크 App ID: {팀 ID}.{번들 ID} */
        public String toAppId() {
            return teamId + "." + bundleId;
        }
    }

    /** Android: 패키지 이름, 앱 서명 인증서 SHA-256 지문 목록 (Play 앱 서명 키 + 업로드 키) */
    public record Android(String packageName, List<String> sha256CertFingerprints) {

        public Android {
            sha256CertFingerprints = sha256CertFingerprints == null ? List.of() : List.copyOf(sha256CertFingerprints);
        }
    }

    /** 경로 > 공개 절대 주소 (예: /a/1 > https://api.secbrief.suhsaechan.kr/a/1) */
    public String toPublicUrl(String path) {
        return publicBaseUrl + path;
    }
}
