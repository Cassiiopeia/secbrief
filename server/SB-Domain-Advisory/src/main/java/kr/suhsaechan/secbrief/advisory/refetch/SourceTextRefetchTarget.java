package kr.suhsaechan.secbrief.advisory.refetch;

/**
 * 요약 입력을 다시 받을 기사 1건: 출처 코드, 저장된 제목(내용 없는 입력 판정 기준), 원문 주소, CVE ID(NVD 만, 없으면 null)
 */
public record SourceTextRefetchTarget(String sourceCode, String title, String originalUrl, String cveId) {
}
