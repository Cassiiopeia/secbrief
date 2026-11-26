package kr.suhsaechan.secbrief.member.push;

/**
 * 푸시 1건 발송 결과: 접수됨, 무효 토큰(기기 삭제 대상), 실패(다음에 다시)
 */
public enum PushSendStatus {
    ACCEPTED,
    DEVICE_NOT_REGISTERED,
    FAILED
}
