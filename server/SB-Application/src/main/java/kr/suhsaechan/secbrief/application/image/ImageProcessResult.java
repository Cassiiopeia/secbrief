package kr.suhsaechan.secbrief.application.image;

/**
 * 이미지 묶음 처리 결과: 저장 건수, 실패 건수, 건너뜀 여부 (저장소 미설정)
 */
public record ImageProcessResult(int storedCount, int failedCount, boolean isSkipped) {
}
