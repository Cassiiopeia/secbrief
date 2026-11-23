package kr.suhsaechan.secbrief.advisory.service;

/**
 * 저장 결과: 새로 저장, 기존 CVE 행에 병합, 이미 있음
 */
public enum SaveOutcome {
    CREATED,
    MERGED,
    DUPLICATE
}
