package kr.suhsaechan.secbrief.member.constant;

/**
 * 알림 종류 (이름이 그대로 API 값·DB 값): 기기가 켜고 끄는 3개 + 관심 기술 묶음 표시 1개
 * KISA_URGENT KISA 긴급 공지, KEV 악용 중인 취약점, CRITICAL_CVE 그 밖의 고위험 취약점(CVSS 9.0 이상, 관심 밖),
 * INTEREST 관심 기술 소식 (알림 기록의 묶음 이름, 기기가 켜고 끄는 값이 아님)
 */
public enum AlertCategory {
    KISA_URGENT,
    KEV,
    CRITICAL_CVE,
    INTEREST;

    /** 기기가 켜고 끄는 종류인지 (INTEREST 는 관심 기술 설정을 따름) */
    public boolean isDeviceToggle() {
        return this != INTEREST;
    }
}
