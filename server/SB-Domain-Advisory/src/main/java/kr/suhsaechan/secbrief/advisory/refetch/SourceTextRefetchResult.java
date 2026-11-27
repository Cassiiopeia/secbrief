package kr.suhsaechan.secbrief.advisory.refetch;

/**
 * 요약 입력 다시 받기 결과 1건: 상태 + 요약 입력(RECOVERED) 또는 정리만 한 글(TOO_THIN) + 실패 사유(영어 고정 문구)
 */
public record SourceTextRefetchResult(Status status, String sourceText, String failureReason) {

    public enum Status {
        // 요약할 내용이 있는 입력 (SourceTextPolicy 통과)
        RECOVERED,
        // 받았지만 요약할 내용이 없음 (40자 미만, 주소뿐, 제목에 통째로 들어 있음)
        TOO_THIN,
        // 받지 못함 (응답 오류, 페이지에 본문 칸 없음, 주소에서 권고 번호를 못 읽음)
        NOT_FOUND,
        // 다시 받지 않는 출처
        NOT_SUPPORTED
    }

    public static SourceTextRefetchResult recovered(String sourceText) {
        return new SourceTextRefetchResult(Status.RECOVERED, sourceText, null);
    }

    public static SourceTextRefetchResult tooThin(String cleanedText) {
        return new SourceTextRefetchResult(Status.TOO_THIN, cleanedText, null);
    }

    public static SourceTextRefetchResult notFound(String failureReason) {
        return new SourceTextRefetchResult(Status.NOT_FOUND, null, failureReason);
    }

    public static SourceTextRefetchResult notSupported() {
        return new SourceTextRefetchResult(Status.NOT_SUPPORTED, null, "source not supported");
    }

    public boolean isRecovered() {
        return status == Status.RECOVERED;
    }
}
