package kr.suhsaechan.secbrief.advisory.constant;

/**
 * 대응 모아보기 구역 3개 (선언 순서 = 화면 순서): 공식 대응 > 확인된 정보 > 관련 기사
 * 판정은 ResponseGuidePolicy.toSection 한 곳 (출처 종류 기준)
 */
public enum ResponseGuideSection {
    // 공식 대응: KISA 공지·취약점 정보·보고서, 제조사·배포판 보안 권고 (출처 종류 ADVISORY)
    OFFICIAL,
    // 확인된 정보: NVD·CISA KEV·GitHub 권고의 CVE 항목 (출처 종류 VULNERABILITY)
    FACT,
    // 관련 기사: 언론 보도 (출처 종류 NEWS, 제목·링크·직접 쓴 요약만)
    NEWS
}
