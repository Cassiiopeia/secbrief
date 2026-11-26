package kr.suhsaechan.secbrief.advisory.ai;

import java.util.Map;

/**
 * 제공자에 보낼 요청 1회분: 지시문, 사용자 메시지(외부 입력을 담은 JSON), 출력 JSON 스키마, 출력 토큰 상한
 */
public record StructuredCompletionRequest(String systemInstruction, String userMessage,
                                          Map<String, Object> outputJsonSchema, int maximumOutputTokens) {
}
