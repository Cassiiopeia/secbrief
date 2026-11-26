package kr.suhsaechan.secbrief.advisory.dto;

import kr.suhsaechan.secbrief.advisory.fact.SourceFact;

/**
 * 사실 값 응답 1개: 라벨(한국어 고정 문구) + 값(원문 문자열 그대로, AI 가 만들지 않음)
 */
public record SourceFactResponse(String label, String value) {

    public static SourceFactResponse from(SourceFact sourceFact) {
        return new SourceFactResponse(sourceFact.label(), sourceFact.value());
    }
}
