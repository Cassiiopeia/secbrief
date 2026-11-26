package kr.suhsaechan.secbrief.advisory.summary;

/**
 * 검증에 걸린 곳 1개: 출력 필드 이름(summary, keyPoints, actionGuide), 사유(로그용 영어 고정 문구), 걸린 글 조각(없으면 null)
 * 고쳐 쓰기 요청에 그대로 넘김 (fragment: 입력에 없는 숫자 표기 목록 또는 입력과 똑같은 구간)
 */
public record SummaryProblem(String fieldName, String reason, String fragment) {
}
