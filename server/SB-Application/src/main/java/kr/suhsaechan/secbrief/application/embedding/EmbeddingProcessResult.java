package kr.suhsaechan.secbrief.application.embedding;

/**
 * 임베딩 묶음 처리 결과: 만든 건수, 건너뜀 여부 (제공자 미설정)
 */
public record EmbeddingProcessResult(int embeddedCount, boolean isSkipped) {
}
