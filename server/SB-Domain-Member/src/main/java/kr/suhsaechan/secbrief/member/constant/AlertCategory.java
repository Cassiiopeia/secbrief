package kr.suhsaechan.secbrief.member.constant;

/**
 * 알림 종류: 기기가 종류별로 켜고 끔 (이름이 그대로 API 값·DB 값)
 * 종류 3개: KISA 긴급 공지, 악용 중인 취약점(KEV 등재), 고위험 취약점(CVSS 9.0 이상)
 */
public enum AlertCategory {
    KISA_URGENT,
    KEV,
    CRITICAL_CVE
}
