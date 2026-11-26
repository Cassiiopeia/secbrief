package kr.suhsaechan.secbrief.advisory.constant;

/**
 * 기사 대표 이미지 상태: 없음 / 대기 > 저장됨, 3회 실패 시 실패 (실패해도 기사는 이미지 없이 계속 노출)
 */
public enum ImageStatus {
    // 피드가 이미지 주소를 주지 않음
    NONE,
    PENDING,
    STORED,
    FAILED
}
