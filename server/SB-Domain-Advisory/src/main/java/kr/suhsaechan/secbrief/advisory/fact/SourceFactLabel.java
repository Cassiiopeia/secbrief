package kr.suhsaechan.secbrief.advisory.fact;

/**
 * 사실 값 라벨 단일 원본 (앱 상세 화면에 그대로 보이는 한국어 문구)
 * GHSA 4개, KEV 4개, NVD 3개, 공통 2개
 */
public final class SourceFactLabel {

    // GHSA: 패키지 이름 (생태계)
    public static final String AFFECTED_PACKAGE = "영향 패키지";
    // GHSA: vulnerable_version_range
    public static final String AFFECTED_VERSION = "영향 버전";
    // GHSA: first_patched_version
    public static final String PATCHED_VERSION = "조치 버전";
    // KEV: vendorProject + product
    public static final String VENDOR_PRODUCT = "제조사·제품";
    // KEV: requiredAction
    public static final String REQUIRED_ACTION = "조치 요구";
    // KEV: dueDate
    public static final String ACTION_DUE_DATE = "조치 기한";
    // KEV: knownRansomwareCampaignUse (Known, Unknown)
    public static final String RANSOMWARE_USE = "랜섬웨어 악용";
    // NVD: cvssData.baseScore, version, vectorString
    public static final String CVSS_SCORE = "CVSS 점수";
    public static final String CVSS_VERSION = "CVSS 버전";
    public static final String CVSS_VECTOR = "CVSS 벡터";
    // GHSA severity, NVD baseSeverity
    public static final String SEVERITY = "심각도";
    // KEV cwes, NVD weaknesses
    public static final String WEAKNESS = "CWE";

    // 출처가 값을 주지 않은 항목의 표시 (지어내지 않음)
    public static final String ABSENT_VALUE = "원문에 없음";

    private SourceFactLabel() {
    }
}
