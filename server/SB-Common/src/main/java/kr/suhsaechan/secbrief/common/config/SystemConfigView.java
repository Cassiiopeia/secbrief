package kr.suhsaechan.secbrief.common.config;

/**
 * 관리자 화면용 설정 1건: 지금 값(SECRET 은 가린 값), 기본값, 값의 출처, 입력 규칙
 * displayValue 에 비밀값 평문·암호문은 들어가지 않음
 */
public record SystemConfigView(ConfigKey key, String displayValue, String defaultValue, boolean isStored,
                               boolean hasSecretValue, ConfigValueSource valueSource) {

    /** 지금 값의 출처 */
    public enum ConfigValueSource {
        // 설정 저장소(DB)에 저장한 값
        STORED,
        // 운영 설정(application.yml·환경변수)의 값
        DEPLOY_PROPERTY,
        // ConfigKey 기본값
        CODE_DEFAULT
    }
}
