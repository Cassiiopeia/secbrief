package kr.suhsaechan.secbrief.advisory.constant;

/**
 * 출처 코드 상수: source.code 시드값과 같아야 함 (V0.3.0, V0.5.0 마이그레이션)
 */
public final class SourceCode {

    // KISA 보호나라 4곳: 보안공지, 취약점 정보, 보고서·가이드, 경보단계 (코드가 모두 KISA 로 시작)
    public static final String KISA = "KISA";
    public static final String KISA_VULNERABILITY = "KISA_VULNERABILITY";
    public static final String KISA_REPORT = "KISA_REPORT";
    public static final String KISA_ALERT_LEVEL = "KISA_ALERT_LEVEL";
    public static final String CISA_KEV = "CISA_KEV";
    public static final String NVD = "NVD";
    public static final String DAILYSECU = "DAILYSECU";
    public static final String THE_HACKER_NEWS = "THE_HACKER_NEWS";
    public static final String BLEEPING_COMPUTER = "BLEEPING_COMPUTER";
    // GitHub 보안 권고 2곳: critical 등급, high 등급 (긴급 여부는 등급이 아니라 점수·KEV 규칙으로 판정)
    public static final String GHSA = "GHSA";
    public static final String GHSA_HIGH = "GHSA_HIGH";
    // 운영체제·런타임 공식 보안 권고 4곳 (해외 공지: kind ADVISORY, region OVERSEAS)
    public static final String UBUNTU_USN = "UBUNTU_USN";
    public static final String DEBIAN_DSA = "DEBIAN_DSA";
    public static final String REDHAT_RHSA = "REDHAT_RHSA";
    public static final String NODEJS_SECURITY = "NODEJS_SECURITY";

    private SourceCode() {
    }

    /** KISA 보호나라 출처인지 (4곳 공통) */
    public static boolean isKisa(String sourceCode) {
        return sourceCode != null && sourceCode.startsWith(KISA);
    }
}
