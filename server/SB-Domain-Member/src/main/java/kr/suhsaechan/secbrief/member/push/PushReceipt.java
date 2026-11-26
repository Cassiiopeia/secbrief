package kr.suhsaechan.secbrief.member.push;

/**
 * 접수증 1건: 실제 전달 결과 (errorCode 는 실패일 때 제공자가 준 코드, 예: InvalidCredentials)
 */
public record PushReceipt(PushSendStatus status, String errorCode) {
}
