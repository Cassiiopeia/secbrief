package kr.suhsaechan.secbrief.advisory.ai.calllog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 호출 용도 (ai_call_log.call_type 값 = 이름): 화면 표시 이름
 */
@Getter
@RequiredArgsConstructor
public enum AiCallType {
    TITLE_ANALYSIS("제목 분석"),
    // 한자가 섞인 번역 제목 고쳐 쓰기 (제목 분석 뒤 1회, #87)
    TITLE_TRANSLATION_REPAIR("제목 번역 고쳐 쓰기"),
    SUMMARY("요약");

    private final String label;
}
