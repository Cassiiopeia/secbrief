package kr.suhsaechan.secbrief.advisory.ai;

/**
 * AI 가 고를 수 있는 기술 분야 1개 (카탈로그 FIELD 항목): code + 이름 + 예시 낱말
 */
public record AiFieldOption(String code, String name, String exampleTerms) {
}
