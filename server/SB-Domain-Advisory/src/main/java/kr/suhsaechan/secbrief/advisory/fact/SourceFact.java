package kr.suhsaechan.secbrief.advisory.fact;

/**
 * 원문 데이터에 있는 사실 값 1개: 라벨(한국어 고정 문구, SourceFactLabel) + 값(원문 문자열 그대로)
 * AI 를 거치지 않음 (영향 버전·조치 버전·조치 기한 등을 지어내지 않게)
 */
public record SourceFact(String label, String value) {
}
