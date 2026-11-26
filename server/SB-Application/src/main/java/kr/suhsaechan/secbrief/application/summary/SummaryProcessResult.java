package kr.suhsaechan.secbrief.application.summary;

/**
 * 요약 묶음 처리 결과: 성공 건수, 실패 건수, 건너뜀 여부 (제공자 미설정·하루 예산 소진)
 */
public record SummaryProcessResult(int doneCount, int failedCount, boolean isSkipped) {
}
