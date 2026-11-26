package kr.suhsaechan.secbrief.advisory.repository;

/**
 * 대응 모아보기 후보 1건 (조회 SQL 결과): 기사 ID, 묶인 이유 순위 (0 같은 CVE, 1 같은 제품, 2 같은 제조사 공지)
 */
public interface ResponseGuideCandidate {

    Long getArticleId();

    Integer getMatchRank();
}
