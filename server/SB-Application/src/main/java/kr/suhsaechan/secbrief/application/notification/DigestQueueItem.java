package kr.suhsaechan.secbrief.application.notification;

/**
 * 아침 요약에 넣을 기사 1건: 기기 번호 + 기사 번호
 */
public record DigestQueueItem(long pushDeviceId, long articleId) {
}
