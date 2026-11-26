package kr.suhsaechan.secbrief.advisory.ai.key;

/**
 * 키 목록 변경 1건의 대상: 키 id, (바뀐 뒤의) 별명. 감사 기록·화면 안내용이라 키 값은 없음
 */
public record AnthropicApiKeyChange(String keyId, String nickname) {
}
