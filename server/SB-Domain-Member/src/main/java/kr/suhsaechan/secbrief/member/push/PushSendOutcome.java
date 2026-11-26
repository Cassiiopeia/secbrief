package kr.suhsaechan.secbrief.member.push;

/**
 * 토큰별 발송 결과: ticketId 는 접수됨일 때만 값 있음 (접수증 조회용)
 */
public record PushSendOutcome(String pushToken, PushSendStatus status, String ticketId) {

    public static PushSendOutcome accepted(String pushToken, String ticketId) {
        return new PushSendOutcome(pushToken, PushSendStatus.ACCEPTED, ticketId);
    }

    public static PushSendOutcome deviceNotRegistered(String pushToken) {
        return new PushSendOutcome(pushToken, PushSendStatus.DEVICE_NOT_REGISTERED, null);
    }

    public static PushSendOutcome failed(String pushToken) {
        return new PushSendOutcome(pushToken, PushSendStatus.FAILED, null);
    }
}
