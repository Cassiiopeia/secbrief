package kr.suhsaechan.secbrief.advisory.constant;

/**
 * 대응 모아보기에 묶인 이유 3개 (선언 순서 = 확실한 순서): 같은 CVE > 같은 제품 > 같은 제조사 공지
 * 조회 SQL 의 match_rank(0, 1, 2)와 같은 순서 (ArticleRepository.findResponseGuideCandidates)
 */
public enum ResponseGuideMatchReason {
    // CVE ID 일치 (전용 열 또는 제목)
    SAME_CVE,
    // 회사·제품 이름이 같음 (정리한 이름 기준, 게시일 ±14일)
    SAME_PRODUCT,
    // KISA 제조사 단위 공지(「X 제품 보안 업데이트 권고」)의 제조사 이름 = 상대 제품 이름의 앞 낱말 (게시일 ±14일)
    SAME_VENDOR;

    /** 조회 SQL 의 match_rank > 이유 (범위 밖은 예외: SQL 과 어긋난 것) */
    public static ResponseGuideMatchReason fromMatchRank(int matchRank) {
        return values()[matchRank];
    }
}
